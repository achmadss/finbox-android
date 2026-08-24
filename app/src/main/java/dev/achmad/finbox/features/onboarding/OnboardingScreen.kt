package dev.achmad.finbox.features.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import dev.achmad.finbox.R
import dev.achmad.finbox.core.parser.AvailableParser
import dev.achmad.finbox.features.onboarding.content.OnboardingAuthContent
import dev.achmad.finbox.features.onboarding.content.OnboardingInstallParsersContent
import dev.achmad.finbox.features.onboarding.content.OnboardingNotificationPermissionContent
import dev.achmad.finbox.features.transaction.list.TransactionsScreen
import dev.achmad.finbox.theme.AppTheme
import dev.achmad.finbox.util.permission.rememberNotificationPermissionState
import soup.compose.material.motion.animation.materialSharedAxisX
import soup.compose.material.motion.animation.rememberSlideDistance

object OnboardingScreen: Screen {
    private fun readResolve(): Any = OnboardingScreen

    override val key: ScreenKey = uniqueScreenKey

    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { OnboardingScreenModel() }
        val state by screenModel.state.collectAsState()
        val activity = LocalActivity.current
        val notificationPermission = rememberNotificationPermissionState()

        val signIn = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result -> screenModel.onSignInResult(result.data) }

        // The last step is leaving: nothing else is waiting on this screen, so the
        // state saying so is the whole signal.
        LaunchedEffect(state) {
            if (state is OnboardingScreenModel.State.Done) navigator.replace(TransactionsScreen)
        }

        // The grant lands on the activity result, not on the button press.
        LaunchedEffect(notificationPermission.status.isGranted, state) {
            if (state is OnboardingScreenModel.State.NotificationPermission &&
                notificationPermission.status.isGranted
            ) {
                screenModel.onNotificationPromptSettled()
            }
        }

        OnboardingScreenContent(
            state = state,
            onClickSignIn = {
                screenModel.onSignInStarted()
                signIn.launch(screenModel.authorizationIntent())
            },
            onClickAllowNotification = notificationPermission::launchPermissionRequest,
            onNotificationPromptSettled = screenModel::onNotificationPromptSettled,
            onRefreshParsers = screenModel::onRefreshParsers,
            onInstallParsers = screenModel::onInstallParsers,
            onExit = { activity?.finish() },
        )
    }
}

@Composable
fun OnboardingScreenContent(
    state: OnboardingScreenModel.State,
    onClickSignIn: () -> Unit = {},
    onClickAllowNotification: () -> Unit = {},
    onNotificationPromptSettled: () -> Unit = {},
    onRefreshParsers: () -> Unit = {},
    onInstallParsers: (List<AvailableParser>) -> Unit = {},
    onExit: () -> Unit = {},
) {
    val slideDistance = rememberSlideDistance()
    var confirmExit by remember { mutableStateOf(false) }

    // Nothing to go back to — the only step left is leaving, and half-finished
    // setup resumes from where it stopped.
    BackHandler { confirmExit = true }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.onboarding_exit_title)) },
            text = { Text(stringResource(R.string.onboarding_exit_message)) },
            confirmButton = {
                TextButton(onClick = onExit) {
                    Text(stringResource(R.string.onboarding_exit_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmExit = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    AnimatedContent(
        targetState = state,
        // A step that only changes its own flags (installing, say) isn't a slide.
        contentKey = { it::class },
        transitionSpec = {
            // The first step to resolve is arrived at, not moved to.
            if (initialState is OnboardingScreenModel.State.Resolving) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                materialSharedAxisX(
                    forward = true,
                    slideDistance = slideDistance,
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { onboardingState ->
        when(onboardingState) {
            // Nothing to draw for either: one is before the first step, the other after
            // the last, and both last a frame while the navigator catches up.
            is OnboardingScreenModel.State.Resolving,
            is OnboardingScreenModel.State.Done -> Unit
            is OnboardingScreenModel.State.SignIn -> {
                OnboardingAuthContent(
                    signingIn = onboardingState.isSigningIn,
                    onClickSignIn = onClickSignIn,
                )
            }
            is OnboardingScreenModel.State.NotificationPermission -> {
                OnboardingNotificationPermissionContent(
                    onClickAllowNotification = onClickAllowNotification,
                    onSkipNotificationPermission = onNotificationPromptSettled,
                )
            }
            is OnboardingScreenModel.State.InstallParsers -> {
                OnboardingInstallParsersContent(
                    parsers = onboardingState.parsers,
                    loading = onboardingState.isLoading,
                    installing = onboardingState.isInstalling,
                    onRefresh = onRefreshParsers,
                    onClickInstallParsers = onInstallParsers,
                )
            }
        }
    }
}

@Preview
@Composable
private fun OnboardingSignInPreview() {
    AppTheme {
        OnboardingScreenContent(state = OnboardingScreenModel.State.SignIn())
    }
}

@Preview
@Composable
private fun OnboardingNotificationPreview() {
    AppTheme {
        OnboardingScreenContent(state = OnboardingScreenModel.State.NotificationPermission)
    }
}

@Preview
@Composable
private fun OnboardingInstallParsersPreview() {
    AppTheme {
        OnboardingScreenContent(
            state = OnboardingScreenModel.State.InstallParsers(parsers = emptyList()),
        )
    }
}
