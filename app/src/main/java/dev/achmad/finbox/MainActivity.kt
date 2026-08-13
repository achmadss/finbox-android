package dev.achmad.finbox

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
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
import dev.achmad.finbox.core.statement.StatementUpdateStatus
import dev.achmad.finbox.features.home.HomeScreen
import dev.achmad.finbox.features.onboarding.OnboardingPreference
import dev.achmad.finbox.features.onboarding.OnboardingScreen
import dev.achmad.finbox.theme.AppTheme
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import soup.compose.material.motion.animation.materialSharedAxisX
import soup.compose.material.motion.animation.rememberSlideDistance

class MainActivity : ComponentActivity() {

    private val onboardingPreference: OnboardingPreference by _root_ide_package_.dev.achmad.finbox.util.koin.injectLazy()
    private val statementUpdateStatus by lazy { StatementUpdateStatus(applicationContext) }

    private var isReady = false
    private var initialScreen: Screen = HomeScreen

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

                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AppStateBanner(text = importingText)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
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
        initialScreen = if (onboardingPreference.onboardingComplete().get()) HomeScreen else OnboardingScreen
        isReady = true
    }

    @Composable
    private fun HandleNewIntent(context: Context, navigator: Navigator) {
        LaunchedEffect(Unit) {
            callbackFlow {
                val componentActivity = context as ComponentActivity
                val consumer = Consumer<Intent> { trySend(it) }
                componentActivity.addOnNewIntentListener(consumer)
                awaitClose { componentActivity.removeOnNewIntentListener(consumer) }
            }.collectLatest { handleIntentAction(it, navigator) }
        }
    }

    private fun handleIntentAction(intent: Intent, navigator: Navigator) {
        // Handle intent here
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
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.secondary)
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
