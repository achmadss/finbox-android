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
import dev.achmad.finbox.core.gmail.GmailAuthManager
import dev.achmad.finbox.core.update.transaction.TransactionUpdateManager
import dev.achmad.finbox.util.ui.ToastHelper
import dev.achmad.finbox.util.koin.inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import dev.achmad.finbox.core.preference.OnboardingPreference
import dev.achmad.finbox.util.permission.PermissionHelper

class OnboardingScreenModel(
    private val toastHelper: ToastHelper = inject(),
    private val accountRepository: AccountRepository = inject(),
    private val authManager: GmailAuthManager = inject(),
    private val preferences: OnboardingPreference = inject(),
    private val transactionUpdateManager: TransactionUpdateManager = inject(),
    private val permissionHelper: PermissionHelper = inject(),
    private val classifier: TransactionClassifier = inject(),
): StateScreenModel<OnboardingScreenModel.State>(State.Resolving) {
    init {
        screenModelScope.launch {
            next()

            // Sign-in finishes in the browser and lands in AuthCallbackActivity.
            // The account row appearing is the only signal this screen gets.
            accountRepository.accounts().collect {
                if (state.value is State.SignIn && it.isNotEmpty()) next()
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

    /**
     * Moves to whichever step is still unfinished, or off the screen when none is.
     * Every transition re-resolves rather than stepping forward one state, so a step
     * finished in an earlier session or in the browser is skipped for free.
     */
    private suspend fun next() {
        val resolved = resolve()
        if (resolved != null) {
            mutableState.value = resolved
            return
        }
        // Nothing left to ask: remember that, and start the first import on the
        // way out so the ledger is filling before Home is drawn.
        preferences.onboardingComplete().set(true)
        // The schedule turns itself away until that flag is set, so it is asked for here
        // rather than waiting for the next app start.
        transactionUpdateManager.schedule()
        // Not a user refresh, and there may be nothing to fetch yet: onboarding
        // no longer installs a source, so a first run reaches Home with none.
        // The home screen says so; this just starts whatever can start.
        transactionUpdateManager.runNow(userInitiated = false)
        mutableState.value = State.Done
    }

    /**
     * Account and permissions only.
     *
     * Installing a source used to be a step here, and under the app-install
     * model that is a REQUEST_INSTALL_PACKAGES grant plus one system dialog per
     * bank before the user has seen anything. So it moved out: the home screen
     * says a source is needed and links to the list, which is one prompt at
     * a moment the user asked for it.
     */
    private suspend fun resolve(): State? = when {
        accountRepository.all().isEmpty() -> State.SignIn()
        !notificationSettled() -> State.NotificationPermission
        // Last, and the only step that asks for nothing: a provider already set
        // up, or the offer already made, both count as settled.
        !classifier.isConfigured() && !preferences.aiPromptSeen().get() -> State.SetupAi
        else -> null
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
        /** Setup is finished; the screen leaves for the transaction list. */
        object Done: State()
    }

}
