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
import dev.achmad.finbox.core.parser.ParserUpdateChecker
import dev.achmad.finbox.core.update.app.AppUpdateChecker
import dev.achmad.finbox.core.parser.ParserUpdateNotifier
import dev.achmad.finbox.core.update.transaction.TransactionUpdateNotifier
import dev.achmad.finbox.core.update.transaction.TransactionUpdateStatus
import dev.achmad.finbox.features.transaction.list.TransactionsScreen
import dev.achmad.finbox.features.parser.list.ParsersScreen
import dev.achmad.finbox.core.preference.OnboardingPreference
import dev.achmad.finbox.features.onboarding.OnboardingScreen
import dev.achmad.finbox.theme.AppThemeFromPreferences
import dev.achmad.finbox.util.koin.injectLazy
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import soup.compose.material.motion.animation.materialSharedAxisX
import soup.compose.material.motion.animation.rememberSlideDistance

/** AppCompatActivity rather than ComponentActivity: below Android 13 it applies the per-app language. */
class MainActivity : AppCompatActivity() {

    private val onboardingPreference: OnboardingPreference by injectLazy()
    private val parserUpdateChecker: ParserUpdateChecker by injectLazy()
    private val appUpdateChecker: AppUpdateChecker by injectLazy()
    private val transactionUpdateStatus: TransactionUpdateStatus by injectLazy()

    private var isReady = false
    private var initialScreen: Screen = TransactionsScreen

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
            AppThemeFromPreferences {
                val slideDistance = rememberSlideDistance()

                val imported by transactionUpdateStatus.imported.collectAsState(initial = null)
                val importingText = imported?.let { count ->
                    stringResource(R.string.transaction_update_importing_progress, count)
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
        // Onboarding sets its flag on the last step, so anything short of that re-opens it.
        initialScreen = if (onboardingPreference.onboardingComplete().get()) TransactionsScreen else OnboardingScreen
        isReady = true
    }

    /** Each checker throttles itself to a day, so this is cheap on most starts. */
    @Composable
    private fun CheckForUpdates() {
        LaunchedEffect(Unit) {
            runCatching { parserUpdateChecker.checkForUpdates() }
                .onFailure { Log.e("Parsers", "Parser update check failed", it) }
            appUpdateChecker.checkAndNotify()
        }
    }

    @Composable
    private fun HandleNewIntent(context: Context, navigator: Navigator) {
        LaunchedEffect(Unit) {
            // A cold start delivers the intent here; a warm start arrives via addOnNewIntentListener.
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
            ParserUpdateNotifier.ACTION_OPEN_PARSERS -> {
                if (navigator.lastItem is TransactionsScreen) navigator.push(ParsersScreen)
            }
            // The list is the root, so whatever was open on top of it goes.
            TransactionUpdateNotifier.ACTION_OPEN_TRANSACTIONS -> {
                if (navigator.items.firstOrNull() is TransactionsScreen) navigator.popUntilRoot()
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
                    .background(MaterialTheme.colorScheme.primary)
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
