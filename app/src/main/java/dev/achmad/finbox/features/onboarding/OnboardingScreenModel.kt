package dev.achmad.finbox.features.onboarding

import android.Manifest
import android.content.Intent
import android.util.Log
import android.widget.Toast
import android.content.Context
import android.os.Build
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.repository.AccountRepository
import dev.achmad.finbox.R
import dev.achmad.finbox.core.extension.AvailableExtension
import dev.achmad.finbox.core.extension.ExtensionManager
import dev.achmad.finbox.core.gmail.GmailAuthManager
import dev.achmad.finbox.util.ui.MviScreenModel
import dev.achmad.finbox.core.statement.StatementUpdateJob
import dev.achmad.finbox.util.ui.ToastHelper
import dev.achmad.finbox.util.permission.arePermissionsAllowed
import dev.achmad.finbox.util.koin.inject
import dev.achmad.finbox.util.koin.injectAndroidContext
import kotlinx.coroutines.launch
import dev.achmad.finbox.core.preference.OnboardingPreference

class OnboardingScreenModel(
    private val toastHelper: ToastHelper = inject(),
    private val accountRepository: AccountRepository = inject(),
    private val extensionManager: ExtensionManager = inject(),
    private val authManager: GmailAuthManager = inject(),
    private val preferences: OnboardingPreference = inject(),
    private val context: Context = injectAndroidContext(),
): MviScreenModel<OnboardingScreenModel.State, OnboardingScreenModel.Event, OnboardingScreenModel.Effect>(
    State.Resolving
) {
    init {
        screenModelScope.launch {
            // What's on disk decides which step is next, and the index fills the
            // extension list; neither is known when the screen is constructed.
            runCatching { extensionManager.reload() }
            next()

            // Sign-in finishes in the browser and lands in AuthCallbackActivity.
            // The account row appearing is the only signal this screen gets.
            accountRepository.accounts().collect {
                if (state.value is State.SignIn && it.isNotEmpty()) next()
            }
        }
    }

    override fun handleEvent(event: Event) {
        when(event) {
            is Event.OnSignInResult -> screenModelScope.launch {
                // Null data is the user backing out of the browser; nothing to say.
                val data = event.data ?: return@launch
                runCatching { authManager.handleCallback(data) }
                    .onSuccess {
                        toastHelper.show(
                            context.getString(R.string.onboarding_auth_connected, it.email)
                        )
                    }
                    .onFailure {
                        Log.e("Onboarding", "Sign-in failed", it)
                        toastHelper.show(
                            message = context.getString(
                                R.string.onboarding_auth_failed,
                                it.message.orEmpty(),
                            ),
                            duration = Toast.LENGTH_LONG,
                        )
                    }
                next()
            }
            is Event.OnRequestNotificationPermission,
            is Event.OnSkipNotificationPermission -> screenModelScope.launch {
                preferences.notificationPromptSeen().set(true)
                next()
            }
            is Event.OnRefreshExtensions -> refreshIndex()
            is Event.OnRequestInstallExtensions -> screenModelScope.launch {
                mutableState.value = State.InstallExtensions(isInstalling = true)
                Log.i("Onboarding", "Installing ${event.availableExtensions.map { it.pkg }}")
                for (extension in event.availableExtensions) {
                    runCatching { extensionManager.installAndWait(extension) }
                        .onSuccess { Log.i("Onboarding", "Installed ${extension.pkg}") }
                        .onFailure {
                            toastHelper.show(
                                context.getString(
                                    R.string.onboarding_extensions_install_failed,
                                    extension.name,
                                )
                            )
                        }
                }
                // An APK can install and still not load — an unsupported lib
                // version, a missing parser class. Without this the step just
                // stays put, with the reason sitting unread in loadErrors.
                extensionManager.loadErrors.value.forEach { (file, reason) ->
                    Log.e("Onboarding", "$file did not load: $reason")
                }
                next()
            }
        }
    }

    /**
     * Moves to whichever step is still unfinished, or off the screen when none is.
     *
     * Every transition re-resolves rather than stepping forward one state: a step
     * finished in an earlier session, or in the browser, is then skipped for free.
     */
    private suspend fun next() {
        val resolved = resolve()
        if (resolved != null) {
            mutableState.value = resolved
            // What's published changes without the app hearing about it, so the
            // list is fetched on arrival rather than once per screen model.
            if (resolved is State.InstallExtensions) refreshIndex()
            return
        }
        // Nothing left to ask: remember that, and start the first import on the
        // way out so the ledger is filling before Home is drawn.
        preferences.onboardingComplete().set(true)
        StatementUpdateJob.runNow(context)
        emit(Effect.NavigateToHome)
    }

    private suspend fun resolve(): State? = when {
        accountRepository.all().isEmpty() -> State.SignIn
        !notificationSettled() -> State.NotificationPermission
        extensionManager.installedInfo.value.isEmpty() -> State.InstallExtensions()
        else -> null
    }

    /** Refetches the published index, leaving the step's other flags alone. */
    private fun refreshIndex() {
        screenModelScope.launch {
            setLoading(true)
            runCatching { extensionManager.refreshIndex() }
                .onFailure { Log.e("Onboarding", "Extension index fetch failed", it) }
            setLoading(false)
        }
    }

    private fun setLoading(loading: Boolean) {
        val current = state.value
        if (current is State.InstallExtensions) {
            mutableState.value = current.copy(isLoading = loading)
        }
    }

    /** Granted, or already asked once and declined — either way, don't ask again. */
    private fun notificationSettled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.arePermissionsAllowed(listOf(Manifest.permission.POST_NOTIFICATIONS)) ||
            preferences.notificationPromptSeen().get()

    sealed interface Effect {
        object NavigateToHome: Effect
    }

    /** The browser flow to launch for result; its outcome comes back as [Event.OnSignInResult]. */
    fun authorizationIntent(): Intent = authManager.authorizationIntent()

    sealed interface Event {
        data class OnSignInResult(val data: Intent?): Event
        object OnRequestNotificationPermission: Event
        object OnSkipNotificationPermission: Event
        object OnRefreshExtensions: Event
        data class OnRequestInstallExtensions(
            val availableExtensions: List<AvailableExtension>,
        ): Event
    }

    sealed class State {
        /**
         * Before the first [resolve]. Starting on [SignIn] instead would show the
         * sign-in screen, then slide off it, every time an already-signed-in user
         * opens the app.
         */
        object Resolving: State()
        object SignIn: State()
        object NotificationPermission: State()
        data class InstallExtensions(
            val isLoading: Boolean = false,
            val isInstalling: Boolean = false,
        ): State()
    }

}
