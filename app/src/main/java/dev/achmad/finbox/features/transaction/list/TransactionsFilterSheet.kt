package dev.achmad.finbox.features.transaction.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.achmad.data.model.EmailAccount
import dev.achmad.data.model.TransactionType
import dev.achmad.finbox.core.parser.LoadedSource
import dev.achmad.finbox.theme.components.CheckboxItem
import dev.achmad.finbox.theme.components.CollapsibleBox
import dev.achmad.finbox.theme.components.SettingsItemsPaddings
import dev.achmad.finbox.theme.components.SortItem
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import dev.achmad.finbox.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsFilterSheet(
    filter: TransactionFilter,
    accounts: List<EmailAccount>,
    sources: List<LoadedSource>,
    onFilterChange: (TransactionFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = SettingsItemsPaddings.Horizontal, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.action_filter),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onFilterChange(TransactionFilter()) },
                    enabled = filter.isActive,
                ) {
                    Text(stringResource(R.string.action_reset))
                }
            }

            CollapsibleBox(heading = headingOf(R.string.type, filter.types.size)) {
                TransactionType.entries.forEach { type ->
                    CheckboxItem(
                        label = stringResource(type.labelRes),
                        checked = type in filter.types,
                        onClick = {
                            onFilterChange(filter.copy(types = filter.types.toggle(type)))
                        },
                    )
                }
            }

            HorizontalDivider()

            CollapsibleBox(heading = headingOf(R.string.parsers, filter.sourceIds.size)) {
                if (sources.isEmpty()) {
                    EmptySectionHint(stringResource(R.string.filter_no_parsers))
                }
                sources.forEach { source ->
                    CheckboxItem(
                        label = source.name,
                        checked = source.id in filter.sourceIds,
                        onClick = {
                            onFilterChange(
                                filter.copy(sourceIds = filter.sourceIds.toggle(source.id)),
                            )
                        },
                    )
                }
            }

            HorizontalDivider()

            CollapsibleBox(heading = headingOf(R.string.accounts, filter.accountIds.size)) {
                if (accounts.isEmpty()) {
                    EmptySectionHint(stringResource(R.string.filter_no_accounts))
                }
                accounts.forEach { account ->
                    CheckboxItem(
                        label = account.email,
                        checked = account.id in filter.accountIds,
                        onClick = {
                            onFilterChange(
                                filter.copy(accountIds = filter.accountIds.toggle(account.id)),
                            )
                        },
                    )
                }
            }

            HorizontalDivider()

            CollapsibleBox(heading = stringResource(R.string.filter_sort_by)) {
                TransactionSort.entries.forEach { sort ->
                    SortItem(
                        label = stringResource(sort.labelRes),
                        // Only the active sort shows an arrow; tapping it flips the direction.
                        sortDescending = filter.descending.takeIf { sort == filter.sort },
                        onClick = {
                            onFilterChange(
                                if (sort == filter.sort) {
                                    filter.copy(descending = !filter.descending)
                                } else {
                                    filter.copy(sort = sort)
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySectionHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = SettingsItemsPaddings.Horizontal,
            vertical = SettingsItemsPaddings.Vertical,
        ),
    )
}

@Composable
private fun headingOf(@StringRes title: Int, selectedCount: Int): String = when {
    selectedCount > 0 -> stringResource(R.string.filter_section_count, stringResource(title), selectedCount)
    else -> stringResource(title)
}

private fun <T> Set<T>.toggle(value: T): Set<T> =
    if (value in this) this - value else this + value

@get:StringRes
private val TransactionSort.labelRes: Int
    get() = when (this) {
        TransactionSort.DATE -> R.string.date
        TransactionSort.AMOUNT -> R.string.amount
    }
