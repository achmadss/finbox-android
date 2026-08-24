package dev.achmad.finbox.features.transaction.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.achmad.data.model.Transaction
import dev.achmad.finbox.features.transaction.list.labelRes
import dev.achmad.finbox.parser.TransactionType
import dev.achmad.finbox.theme.components.AppBar
import dev.achmad.finbox.util.formatter.formatAmount
import dev.achmad.finbox.util.formatter.formatDate
import dev.achmad.finbox.util.ui.rememberUse24HourClock
import androidx.compose.ui.res.stringResource
import dev.achmad.finbox.R

data class TransactionDetailScreen(private val id: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel(tag = id) { TransactionDetailScreenModel(id) }
        val transaction by model.transaction.collectAsState()
        val types by model.types.collectAsState()
        var confirmDelete by remember { mutableStateOf(false) }

        // Same rule as the account details screen: gone *after* it was here means deleted,
        // whereas gone at the start is just the first read still running.
        var everLoaded by remember { mutableStateOf(false) }
        LaunchedEffect(transaction) {
            if (transaction != null) everLoaded = true else if (everLoaded) navigator.pop()
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(R.string.transaction),
                    navigateUp = navigator::pop,
                    actions = if (transaction == null) {
                        emptyList()
                    } else {
                        listOf(
                            AppBar.Action(
                                title = stringResource(R.string.action_edit),
                                icon = Icons.Outlined.Edit,
                                onClick = { navigator.push(TransactionEditScreen(id)) },
                            ),
                            AppBar.Action(
                                title = stringResource(R.string.action_delete),
                                icon = Icons.Outlined.Delete,
                                onClick = { confirmDelete = true },
                            ),
                        )
                    },
                )
            },
        ) { padding ->
            val current = transaction
            if (current == null) {
                Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                TransactionView(transaction = current, types = types)
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(stringResource(R.string.delete_transaction)) },
                text = { Text(stringResource(R.string.delete_transaction_confirmation)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmDelete = false
                            model.delete()
                        },
                    ) { Text(stringResource(R.string.action_delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }
    }
}

@Composable
private fun TransactionView(transaction: Transaction, types: List<TransactionType>) {
    val use24Hour = rememberUse24HourClock()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = formatAmount(transaction.signedAmount, transaction.currency),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatDate(transaction.timestamp, use24Hour),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
    Field(stringResource(R.string.direction), transaction.direction?.let { stringResource(it.labelRes) })
    // The parser's own word for the type, falling back to the stored key when its
    // parser is gone or dropped it.
    Field(stringResource(R.string.type), types.nameOf(transaction.type) ?: transaction.type)
    Field(stringResource(R.string.category), transaction.category)
    Field(stringResource(R.string.description), transaction.description)
    Field(stringResource(R.string.merchant), transaction.merchant)
    HorizontalDivider()
    Field(stringResource(R.string.label_added), formatDate(transaction.createdAt, use24Hour))
    Field(stringResource(R.string.label_edited), formatDate(transaction.updatedAt, use24Hour))
}

/** One read-only row. An empty value still shows, so the shape of the record is visible. */
@Composable
private fun Field(label: String, value: String?) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value?.takeIf { it.isNotBlank() } ?: "-")
    }
}
