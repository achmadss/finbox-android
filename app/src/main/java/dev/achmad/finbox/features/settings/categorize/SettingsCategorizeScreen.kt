package dev.achmad.finbox.features.settings.categorize

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.achmad.data.model.ClassificationRun
import dev.achmad.data.model.ClassificationScope
import dev.achmad.data.model.ClassificationStatus
import dev.achmad.finbox.R
import dev.achmad.finbox.core.categorization.CategorizationManager
import dev.achmad.finbox.theme.components.AppBar
import dev.achmad.finbox.util.formatter.formatDate
import dev.achmad.finbox.util.ui.rememberUse24HourClock

/**
 * Start a classify pass, and see what every pass has done.
 *
 * The history is not a debug view. A background job that spends someone's money
 * and rewrites their ledger has to be able to account for itself, or the honest
 * advice would be not to turn it on.
 */
object SettingsCategorizeScreen : Screen {
    private fun readResolve(): Any = SettingsCategorizeScreen

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = remember { SettingsCategorizeScreenModel() }
        val runs by model.runs.collectAsState()
        val estimate by model.estimate.collectAsState()
        val progress by model.progress.collectAsState()
        val mine by model.mine.collectAsState()
        val use24Hour = rememberUse24HourClock()
        var confirmRedo by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(R.string.categorize_title),
                    navigateUp = navigator::pop,
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val running = progress as? CategorizationManager.State.Running
                            when {
                                running != null -> {
                                    Text(
                                        stringResource(
                                            R.string.categorize_running,
                                            running.done,
                                            running.total,
                                        ),
                                    )
                                    LinearProgressIndicator(
                                        progress = {
                                            if (running.total == 0) 0f
                                            else running.done.toFloat() / running.total
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }

                                estimate?.transactions == 0 -> Text(
                                    text = stringResource(R.string.categorize_estimate_title),
                                )

                                else -> estimate?.let {
                                    Text(
                                        stringResource(
                                            R.string.categorize_estimate,
                                            it.transactions,
                                            it.signatures,
                                            it.requests,
                                        ),
                                    )
                                    if (it.fromCache > 0) {
                                        Text(
                                            text = stringResource(
                                                R.string.categorize_estimate_cached,
                                                it.fromCache,
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            if (!model.hasProvider) {
                                Text(
                                    text = stringResource(R.string.categorize_no_provider),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (running != null) {
                                    OutlinedButton(onClick = model::cancel) {
                                        Text(stringResource(R.string.categorize_cancel))
                                    }
                                } else {
                                    Button(
                                        onClick = model::start,
                                        enabled = (estimate?.transactions ?: 0) > 0,
                                    ) { Text(stringResource(R.string.categorize_start)) }
                                    OutlinedButton(onClick = { confirmRedo = true }) {
                                        Text(stringResource(R.string.categorize_start_all))
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.categorize_history),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (runs.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.categorize_history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(runs, key = { it.id }) { run ->
                    RunRow(run, use24Hour) { navigator.push(ClassificationRunScreen(run.id)) }
                    HorizontalDivider()
                }
            }
        }

        if (confirmRedo) {
            AlertDialog(
                onDismissRequest = { confirmRedo = false },
                title = { Text(stringResource(R.string.categorize_start_all)) },
                text = {
                    Text(stringResource(R.string.categorize_redo_confirmation, mine))
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmRedo = false
                            model.redoEverything(replaceManual = false)
                        },
                    ) { Text(stringResource(R.string.categorize_redo_keep_mine)) }
                },
                dismissButton = {
                    // The destructive option is the dismiss slot on purpose: it
                    // is the one that should take a deliberate reach.
                    TextButton(
                        onClick = {
                            confirmRedo = false
                            model.redoEverything(replaceManual = true)
                        },
                    ) { Text(stringResource(R.string.categorize_redo_replace)) }
                },
            )
        }
    }

    @Composable
    private fun RunRow(run: ClassificationRun, use24Hour: Boolean, onClick: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDate(run.startedAt, use24Hour),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(
                        when (run.status) {
                            ClassificationStatus.RUNNING -> R.string.categorize_status_running
                            ClassificationStatus.DONE -> R.string.categorize_status_done
                            ClassificationStatus.FAILED -> R.string.categorize_status_failed
                            ClassificationStatus.CANCELLED -> R.string.categorize_status_cancelled
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (run.status == ClassificationStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Text(
                text = stringResource(
                    R.string.categorize_run_summary,
                    run.categorized,
                    run.unknown,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(
                    R.string.categorize_run_cost,
                    run.requests,
                    run.totalTokens
                        ?.takeIf { it > 0 }
                        ?.let { stringResource(R.string.categorize_run_tokens, it) }
                        ?: stringResource(R.string.categorize_run_no_tokens),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val detail = buildList {
                add(
                    stringResource(
                        when (run.scope) {
                            ClassificationScope.UNCATEGORIZED -> R.string.categorize_scope_uncategorized
                            ClassificationScope.SELECTION -> R.string.categorize_scope_selection
                            ClassificationScope.ALL -> R.string.categorize_scope_all
                        },
                    ),
                )
                if (run.signaturesCached > 0) {
                    add(stringResource(R.string.categorize_run_cached, run.signaturesCached))
                }
                run.model?.let { add(it) }
            }
            Text(
                text = detail.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            run.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
