package dev.achmad.finbox.features.settings.categorize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.achmad.data.model.ClassificationOrigin
import dev.achmad.data.model.ClassificationResult
import dev.achmad.data.model.TransactionDirection
import dev.achmad.finbox.R
import dev.achmad.finbox.features.transaction.categoryLabel
import dev.achmad.finbox.theme.components.AppBar
import dev.achmad.finbox.util.formatter.formatAmount
import dev.achmad.finbox.util.formatter.formatDate
import dev.achmad.finbox.util.ui.rememberUse24HourClock

data class ClassificationRunScreen(private val runId: Long) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = remember { ClassificationRunScreenModel(runId) }
        val results by model.results.collectAsState()
        val run by model.run.collectAsState()
        val use24Hour = rememberUse24HourClock()

        Scaffold(
            topBar = {
                AppBar(
                    // The run's own timestamp — the only thing telling two runs apart.
                    title = run?.let { formatDate(it.startedAt, use24Hour) }
                        ?: stringResource(R.string.run_detail_title),
                    navigateUp = navigator::pop,
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                item {
                    Text(
                        text = stringResource(
                            R.string.run_detail_summary,
                            results.size,
                            results.count { it.origin == ClassificationOrigin.ASKED },
                            results.count { it.origin == ClassificationOrigin.CACHED },
                            results.count { it.origin == ClassificationOrigin.NO_INPUT },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                if (results.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.run_detail_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                // Grouped by the decision, so a category filled with rows that
                // do not belong in it is visible without reading every line.
                results.groupBy { it.category }.forEach { (category, rows) ->
                    item(key = "header-${category?.name}") {
                        Text(
                            text = stringResource(
                                R.string.run_detail_group,
                                categoryLabel(category),
                                rows.size,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(rows.size, key = { "${category?.name}-${rows[it].transactionId}" }) {
                        ResultRow(rows[it], use24Hour)
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    @Composable
    private fun ResultRow(result: ClassificationResult, use24Hour: Boolean) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    // What the classifier had to go on, in the order it reads it.
                    // A row with nothing here is the case the run never sent.
                    text = result.merchant
                        ?: result.description
                        ?: stringResource(R.string.run_detail_no_text),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = formatAmount(
                        result.amount?.let {
                            if (result.direction == TransactionDirection.OUTGOING) -it else it
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val evidence = listOfNotNull(
                result.merchant?.let { result.description },
                result.method,
            )
            if (evidence.isNotEmpty()) {
                Text(
                    text = evidence.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = listOfNotNull(
                    result.date?.let { formatDate(it, use24Hour) },
                    stringResource(
                        when (result.origin) {
                            ClassificationOrigin.ASKED -> R.string.run_detail_origin_asked
                            ClassificationOrigin.CACHED -> R.string.run_detail_origin_cached
                            ClassificationOrigin.NO_INPUT -> R.string.run_detail_origin_no_input
                        },
                    ),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
