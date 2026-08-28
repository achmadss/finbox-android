package dev.achmad.finbox.features.onboarding

import android.Manifest
import android.content.Intent
import android.util.Log
import android.widget.Toast
import android.os.Build
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.repository.AccountRepository
import dev.achmad.finbox.R
import dev.achmad.finbox.core.llm.TransactionClassifier
import dev.achmad.finbox.core.extension.AvailableExtension
import dev.achmad.finbox.core.extension.InstallStep
import dev.achmad.finbox.core.extension.ExtensionManager
import dev.achmad.finbox.core.gmail.GmailAuthManager
import dev.achmad.finbox.core.update.transaction.TransactionUpdateManager
import dev.achmad.finbox.util.ui.ToastHelper
import dev.achmad.finbox.util.koin.inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import dev.achmad.finbox.core.preference.OnboardingPreference
import dev.achmad.finbox.util.permission.PermissionHelper

class OnboardingScreenModel(
    private val toastHelper: ToastHelper = inject(),
    private val accountRepository: AccountRepository = inject(),
    private val extensionManager: ExtensionManager = inject(),
    private val authManager: GmailAuthManager = inject(),
    private val preferences: OnboardingPreference = inject(),
    private val transactionUpdateManager: TransactionUpdateManager = inject(),
    private val permissionHelper: PermissionHelper = inject(),
    private val classifier: TransactionClassifier = inject(),
): StateScreenModel<OnboardingScreenModel.State>(State.Resolving) {
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

        // The step renders what the state holds, so the published list is folded
        // in here rather than read from the composition.
        screenModelScope.launch {
            extensionManager.available.collect { available ->
                val current = state.value
                if (current is State.InstallExtensions) {
                    mutableState.value = current.copy(extensions = available)
                }
            }
        }
    }

    /** The browser is up; the step is busy until its result lands. */
    fun onSignInStarted() {
        mutableState.value = State.SignIn(isSigningIn = true)
    }

    fun onSignInResult(data: Intent?) {
        // Null data is the user backing out of the browser.
        if (data == null) {
            mutableState.value = State.SignIn()
            return
        }
        screenModelScope.launch {
            runCatching { authManager.handleCallback(data) }
                .onSuccess {
                    toastHelper.show(R.string.onboarding_auth_connected, it.email)
                }
                .onFailure {
                    Log.e("Onboarding", "Sign-in failed", it)
                    // Back to a step that can be tried again; next() leaves it on SignIn.
                    mutableState.value = State.SignIn()
                    toastHelper.show(
                        R.string.onboarding_auth_failed,
                        it.message.orEmpty(),
                        duration = Toast.LENGTH_LONG,
                    )
                }
            next()
        }
    }

    /**
     * Set up or waved away — either way the offer has been made. Also called on
     * returning from the provider screen, so finishing setup there moves onboarding
     * along without a second confirmation.
     */
    fun onAiPromptSettled() {
        screenModelScope.launch {
            preferences.aiPromptSeen().set(true)
            next()
        }
    }

    /** Granted or skipped — either way the prompt has been seen and the step is done. */
    fun onNotificationPromptSettled() {
        screenModelScope.launch {
            preferences.notificationPromptSeen().set(true)
            next()
        }
    }

    fun onRefreshExtensions() = refreshIndex(settle = 1.seconds)

    fun onInstallExtensions(requested: List<AvailableExtension>) {
        screenModelScope.launch {
            val current = state.value as? State.InstallExtensions ?: State.InstallExtensions()
            mutableState.value = current.copy(isInstalling = true)
            Log.i("Onboarding", "Installing ${requested.map { it.pkg }}")
            // The manager runs the installs, so leaving mid-download does not cancel one; this
            // step only waits for each to land: loaded, or failed with a reason on the row.
            requested.forEach { extensionManager.install(it) }
            // Wait on the install jobs, not the registry: an APK that installs but fails to load
            // never reaches the registry, so waiting there would hang on "Installing" forever.
            extensionManager.installSteps.first { steps ->
                requested.all { steps[it.pkg].let { step -> step == null || step == InstallStep.Error } }
            }

            val steps = extensionManager.installSteps.value
            requested.filter { steps[it.pkg] == InstallStep.Error }.forEach { extension ->
                toastHelper.show(R.string.onboarding_extensions_install_failed, extension.name)
            }
            // An APK can install and still not load — a lib version this build does
            // not support, a missing extension class, or an API that changed under it.
            // Say so — the install itself succeeded.
            val loaded = extensionManager.installedInfo.value
            requested.filter { steps[it.pkg] != InstallStep.Error && it.pkg !in loaded }
                .forEach { extension ->
                    Log.e("Onboarding", "${extension.pkg} installed but did not load")
                    toastHelper.show(
                        R.string.onboarding_extensions_load_failed,
                        extension.name,
                        duration = Toast.LENGTH_LONG,
                    )
                }
            extensionManager.loadErrors.value.forEach { (file, reason) ->
                Log.e("Onboarding", "$file did not load: $reason")
            }
            next()
        }
    }

    /**
     * Moves to whichever step is still unfinished, or off the screen when none is.
     * Every transition re-resolves rather than stepping forward one state, so a step
     * finished in an earlier session or in the browser is skipped for free.
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
        // The schedule turns itself away until that flag is set, so it is asked for here
        // rather than waiting for the next app start.
        transactionUpdateManager.schedule()
        // Not a user refresh: an extension install a moment earlier may still have a re-read running.
        transactionUpdateManager.runNow(userInitiated = false)
        mutableState.value = State.Done
    }

    private suspend fun resolve(): State? = when {
        accountRepository.all().isEmpty() -> State.SignIn()
        !notificationSettled() -> State.NotificationPermission
        extensionManager.installedInfo.value.isEmpty() ->
            State.InstallExtensions(extensions = extensionManager.available.value)
        // Last, and the only step that asks for nothing: a provider already set
        // up, or the offer already made, both count as settled.
        !classifier.isConfigured() && !preferences.aiPromptSeen().get() -> State.SetupAi
        else -> null
    }

    /** Refetches the published index, leaving the step's other flags alone. */
    private fun refreshIndex(settle: Duration = Duration.ZERO) {
        screenModelScope.launch {
            setLoading(true)
            // A pull passes a settle time: the fetch is one small request, and without
            // it the indicator blinks in and back out before it reads as a refresh.
            delay(settle)
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
            permissionHelper.arePermissionsAllowed(listOf(Manifest.permission.POST_NOTIFICATIONS)) ||
            preferences.notificationPromptSeen().get()

    fun hasProvider(): Boolean = classifier.isConfigured()

    fun authorizationIntent(): Intent = authManager.authorizationIntent()

    sealed class State {
        /**
         * Before the first [resolve]. Starting on [SignIn] instead would flash the
         * sign-in screen at an already-signed-in user and then slide off it.
         */
        object Resolving: State()
        /** [isSigningIn] while the browser flow is out and its token exchange runs. */
        data class SignIn(val isSigningIn: Boolean = false): State()
        object NotificationPermission: State()

        /** Nothing here is required. */
        object SetupAi: State()
        data class InstallExtensions(
            /** What the index is offering. Carried here so the step draws from state alone. */
            val extensions: List<AvailableExtension> = emptyList(),
            val isLoading: Boolean = false,
            val isInstalling: Boolean = false,
        ): State()

        /** Setup is finished; the screen leaves for the transaction list. */
        object Done: State()
    }

}
