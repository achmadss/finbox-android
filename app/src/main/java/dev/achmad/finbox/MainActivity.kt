package dev.achmad.finbox

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.stack.StackEvent
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.NavigatorDisposeBehavior
import cafe.adriel.voyager.transitions.ScreenTransition
import dev.achmad.finbox.core.extension.ExtensionUpdateChecker
import dev.achmad.finbox.core.update.AppUpdateChecker
import dev.achmad.finbox.core.extension.ExtensionUpdateNotifier
import dev.achmad.finbox.core.statement.StatementUpdateStatus
import dev.achmad.finbox.features.expenses.ExpensesScreen
import dev.achmad.finbox.features.extensions.ExtensionsScreen
import dev.achmad.finbox.core.preference.OnboardingPreference
import dev.achmad.finbox.features.onboarding.OnboardingScreen
import dev.achmad.finbox.theme.AppTheme
import dev.achmad.finbox.util.koin.injectLazy
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import soup.compose.material.motion.animation.materialSharedAxisX
import soup.compose.material.motion.animation.rememberSlideDistance

/**
 * AppCompat rather than plain ComponentActivity: below Android 13 that is what
 * applies a per-app language to the activity's resources.
 */
class MainActivity : AppCompatActivity() {

    private val onboardingPreference: OnboardingPreference by injectLazy()
    private val extensionUpdateChecker: ExtensionUpdateChecker by injectLazy()
    private val appUpdateChecker: AppUpdateChecker by injectLazy()
    private val statementUpdateStatus by lazy { StatementUpdateStatus(applicationContext) }

    private var isReady = false
    private var initialScreen: Screen = ExpensesScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content: View = findViewById(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    return if (isReady) {
                        content.viewTreeObserver.removeOnPreDrawListener(this)
                        true
                    } else {
                        false
                    }
                }
            }
        )

        handlePreDraw()

        enableEdgeToEdge()
        setContent {
            AppTheme {
                val slideDistance = rememberSlideDistance()

                val imported by statementUpdateStatus.imported.collectAsState(initial = null)
                val importingText = imported?.let { count ->
                    stringResource(R.string.statement_update_importing_progress, count)
                }

                val horizontalInsets = WindowInsets.navigationBars
                    .only(WindowInsetsSides.Horizontal)
                Scaffold(
                    topBar = {
                        AppStateBanner(
                            text = importingText,
                            modifier = Modifier.windowInsetsPadding(horizontalInsets),
                        )
                    },
                    contentWindowInsets = horizontalInsets,
                ) { contentPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                            // The banner already took the status bar inset. Without consuming it,
                            // the screen's own app bar would pad for it a second time.
                            .consumeWindowInsets(contentPadding),
                    ) {
                        Navigator(
                            screen = initialScreen,
                            disposeBehavior = NavigatorDisposeBehavior(
                                disposeNestedNavigators = false,
                                disposeSteps = true,
                            )
                        ) { navigator ->
                            ScreenTransition(
                                modifier = Modifier.fillMaxSize(),
                                navigator = navigator,
                                transition = {
                                    materialSharedAxisX(
                                        forward = navigator.lastEvent != StackEvent.Pop,
                                        slideDistance = slideDistance,
                                    )
                                },
                            )
                            HandleNewIntent(this@MainActivity, navigator)
                            CheckForUpdates()
                        }
                    }
                }
            }
        }
    }

    private fun handlePreDraw() {
        // Handle pre draw here (e.g. Splash Screen, fetch data, etc)
        // Onboarding sets its flag on the last step; anything short of that and
        // it re-opens and works out which step is still missing.
        initialScreen = if (onboardingPreference.onboardingComplete().get()) ExpensesScreen else OnboardingScreen
        isReady = true
    }

    /** Throttled to a day inside each checker, so this costs nothing on most starts. */
    @Composable
    private fun CheckForUpdates() {
        LaunchedEffect(Unit) {
            runCatching { extensionUpdateChecker.checkForUpdates() }
                .onFailure { Log.e("Extensions", "Extension update check failed", it) }
            appUpdateChecker.checkAndNotify()
        }
    }

    @Composable
    private fun HandleNewIntent(context: Context, navigator: Navigator) {
        LaunchedEffect(Unit) {
            // MainActivity is launched standard, so a notification tap recreates it and
            // the intent arrives here rather than through addOnNewIntentListener.
            handleIntentAction(intent, navigator)

            callbackFlow {
                val componentActivity = context as ComponentActivity
                val consumer = Consumer<Intent> { trySend(it) }
                componentActivity.addOnNewIntentListener(consumer)
                awaitClose { componentActivity.removeOnNewIntentListener(consumer) }
            }.collectLatest { handleIntentAction(it, navigator) }
        }
    }

    private fun handleIntentAction(intent: Intent, navigator: Navigator) {
        when (intent.action) {
            // Onboarding has to finish before there is anywhere sensible to land.
            ExtensionUpdateNotifier.ACTION_OPEN_EXTENSIONS -> {
                if (navigator.lastItem is ExpensesScreen) navigator.push(ExtensionsScreen)
            }
        }
    }

    @Composable
    private fun AppStateBanner(
        text: String?,
        modifier: Modifier = Modifier,
    ) {
        AnimatedVisibility(
            visible = text != null,
            enter = expandVertically(),
            exit = shrinkVertically(),
            modifier = modifier,
        ) {
            Row(
                // Background first, so the colour reaches under the status bar and only the
                // content is pushed below it.
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondary)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onSecondary,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text.orEmpty(),
                    color = MaterialTheme.colorScheme.onSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }

}
