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
import dev.achmad.finbox.core.parser.AvailableParser
import dev.achmad.finbox.core.parser.InstallStep
import dev.achmad.finbox.core.parser.ParserManager
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
    private val parserManager: ParserManager = inject(),
    private val authManager: GmailAuthManager = inject(),
    private val preferences: OnboardingPreference = inject(),
    private val transactionUpdateManager: TransactionUpdateManager = inject(),
    private val permissionHelper: PermissionHelper = inject(),
): StateScreenModel<OnboardingScreenModel.State>(State.Resolving) {
    init {
        screenModelScope.launch {
            // What's on disk decides which step is next, and the index fills the
            // parser list; neither is known when the screen is constructed.
            runCatching { parserManager.reload() }
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
            parserManager.available.collect { available ->
                val current = state.value
                if (current is State.InstallParsers) {
                    mutableState.value = current.copy(parsers = available)
                }
            }
        }
    }

    /** The browser is up; the step is busy until its result lands. */
    fun onSignInStarted() {
        mutableState.value = State.SignIn(isSigningIn = true)
    }

    /** What the browser flow came back with. */
    fun onSignInResult(data: Intent?) {
        // Null data is the user backing out of the browser; nothing to say beyond
        // handing the step back.
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

    /** Granted or skipped — either way the prompt has been seen and the step is done. */
    fun onNotificationPromptSettled() {
        screenModelScope.launch {
            preferences.notificationPromptSeen().set(true)
            next()
        }
    }

    fun onRefreshParsers() = refreshIndex(settle = 1.seconds)

    fun onInstallParsers(requested: List<AvailableParser>) {
        screenModelScope.launch {
            val current = state.value as? State.InstallParsers ?: State.InstallParsers()
            mutableState.value = current.copy(isInstalling = true)
            Log.i("Onboarding", "Installing ${requested.map { it.pkg }}")
            // The manager runs them, the same as the parsers screen does, so
            // leaving mid-download does not cancel one. This step only waits
            // for each to land: loaded, or failed with a reason on the row.
            requested.forEach { parserManager.install(it) }
            // Wait on the install jobs alone, never on the registry agreeing.
            // install() marks every package Pending before this runs, and each
            // one leaves that map when its job ends — removed once installed,
            // kept as Error when it failed. An APK that installs and then fails
            // to load never reaches the registry at all, so waiting for it there
            // waits forever, which is what left this step stuck on "Installing".
            parserManager.installSteps.first { steps ->
                requested.all { steps[it.pkg].let { step -> step == null || step == InstallStep.Error } }
            }

            val steps = parserManager.installSteps.value
            requested.filter { steps[it.pkg] == InstallStep.Error }.forEach { parser ->
                toastHelper.show(R.string.onboarding_parsers_install_failed, parser.name)
            }
            // An APK can install and still not load: a lib version this build
            // does not support, a missing parser class, or an API it was built
            // against that has since changed under it. Say so — the install
            // itself succeeded, so nothing else on this screen would.
            val loaded = parserManager.installedInfo.value
            requested.filter { steps[it.pkg] != InstallStep.Error && it.pkg !in loaded }
                .forEach { parser ->
                    Log.e("Onboarding", "${parser.pkg} installed but did not load")
                    toastHelper.show(
                        R.string.onboarding_parsers_load_failed,
                        parser.name,
                        duration = Toast.LENGTH_LONG,
                    )
                }
            parserManager.loadErrors.value.forEach { (file, reason) ->
                Log.e("Onboarding", "$file did not load: $reason")
            }
            next()
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
            if (resolved is State.InstallParsers) refreshIndex()
            return
        }
        // Nothing left to ask: remember that, and start the first import on the
        // way out so the ledger is filling before Home is drawn.
        preferences.onboardingComplete().set(true)
        // The schedule turns itself away until that flag is set, so it is asked for here
        // rather than waiting for the next app start.
        transactionUpdateManager.schedule()
        // Nobody pressed refresh — this is the way out of onboarding, and a parser
        // install a moment earlier may still have its own re-read running.
        transactionUpdateManager.runNow(userInitiated = false)
        mutableState.value = State.Done
    }

    private suspend fun resolve(): State? = when {
        accountRepository.all().isEmpty() -> State.SignIn()
        !notificationSettled() -> State.NotificationPermission
        parserManager.installedInfo.value.isEmpty() ->
            State.InstallParsers(parsers = parserManager.available.value)
        else -> null
    }

    /** Refetches the published index, leaving the step's other flags alone. */
    private fun refreshIndex(settle: Duration = Duration.ZERO) {
        screenModelScope.launch {
            setLoading(true)
            // A pull passes a settle time: the fetch is one small request, and without
            // it the indicator blinks in and back out before it reads as a refresh.
            delay(settle)
            runCatching { parserManager.refreshIndex() }
                .onFailure { Log.e("Onboarding", "Parser index fetch failed", it) }
            setLoading(false)
        }
    }

    private fun setLoading(loading: Boolean) {
        val current = state.value
        if (current is State.InstallParsers) {
            mutableState.value = current.copy(isLoading = loading)
        }
    }

    /** Granted, or already asked once and declined — either way, don't ask again. */
    private fun notificationSettled(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            permissionHelper.arePermissionsAllowed(listOf(Manifest.permission.POST_NOTIFICATIONS)) ||
            preferences.notificationPromptSeen().get()

    /** The browser flow to launch for result; its outcome comes back as [onSignInResult]. */
    fun authorizationIntent(): Intent = authManager.authorizationIntent()

    sealed class State {
        /**
         * Before the first [resolve]. Starting on [SignIn] instead would show the
         * sign-in screen, then slide off it, every time an already-signed-in user
         * opens the app.
         */
        object Resolving: State()
        /** [isSigningIn] while the browser flow is out and its token exchange runs. */
        data class SignIn(val isSigningIn: Boolean = false): State()
        object NotificationPermission: State()
        data class InstallParsers(
            /** What the index is offering. Carried here so the step draws from state alone. */
            val parsers: List<AvailableParser> = emptyList(),
            val isLoading: Boolean = false,
            val isInstalling: Boolean = false,
        ): State()

        /** Setup is finished; the screen leaves for the transaction list. */
        object Done: State()
    }

}
