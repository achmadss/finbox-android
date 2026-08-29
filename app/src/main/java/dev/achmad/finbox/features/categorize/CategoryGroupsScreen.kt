package dev.achmad.finbox.features.categorize

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.achmad.data.model.Signature
import dev.achmad.data.model.SignatureGroup
import dev.achmad.data.model.TransactionDirection
import dev.achmad.finbox.R
import dev.achmad.finbox.features.transaction.detail.CategoryPickerDialog
import dev.achmad.finbox.theme.components.AppBar

/**
 * Signature groups, biggest first, each fileable in one tap.
 *
 * The group screen faces the ledger as it is: the top rows name no counterparty
 * and say no note, and the only person who knows what they were for is the
 * user. The screen does not pretend otherwise — but filing "GOPAY" is still one
 * tap, and leaving it Uncategorized is one too.
 */
object CategoryGroupsScreen : Screen {
    private fun readResolve(): Any = CategoryGroupsScreen

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { CategoryGroupsScreenModel() }
        val groups by model.groups.collectAsState()
        var pickerFor by remember { mutableStateOf<Signature?>(null) }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(R.string.category_groups_title),
                    navigateUp = navigator::pop,
                )
            },
        ) { padding ->
            if (groups.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    Text(
                        text = stringResource(R.string.category_groups_empty),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.category_groups_empty_info),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            text = stringResource(R.string.category_groups_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    items(groups, key = { it.signature.toString() }) { group ->
                        GroupRow(
                            group = group,
                            onPick = {
                                pickerFor = group.signature
                                model.refresh()
                            },
                        )
                    }
                }
            }
        }

        val asked = pickerFor
        if (asked != null) {
            val group = groups.firstOrNull { it.signature == asked }
            if (group != null) {
                CategoryPickerDialog(
                    selected = null,
                    onDismiss = { pickerFor = null },
                    onSelect = { category ->
                        category?.let { model.file(group, it) }
                    },
                    includeUncategorized = false,
                )
            } else {
                pickerFor = null
            }
        }
    }
}

@Composable
private fun GroupRow(
    group: SignatureGroup,
    onPick: () -> Unit,
) {
    val signature = group.signature
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = signature.merchant ?: stringResource(R.string.category_groups_no_merchant),
                style = MaterialTheme.typography.bodyLarge,
            )
            val detail = buildList {
                signature.direction?.let {
                    add(
                        stringResource(
                            when (it) {
                                TransactionDirection.OUTGOING -> R.string.direction_outgoing
                                TransactionDirection.INCOMING -> R.string.direction_incoming
                            },
                        ),
                    )
                }
                add(stringResource(R.string.category_groups_rows, group.rowCount))
            }
            Text(
                text = detail.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Label,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(
                text = stringResource(R.string.category_groups_pick),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
