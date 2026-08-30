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
import dev.achmad.data.model.CategorySource
import dev.achmad.data.model.TransactionCategory
import dev.achmad.finbox.features.transaction.categoryLabel
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionDirection
import dev.achmad.finbox.features.transaction.list.labelRes
import dev.achmad.finbox.theme.components.AppBar
import dev.achmad.finbox.util.formatter.formatAmount
import dev.achmad.finbox.util.formatter.formatDate
import dev.achmad.finbox.util.ui.rememberUse24HourClock
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.achmad.finbox.theme.AppTheme
import dev.achmad.finbox.R

data class TransactionDetailScreen(private val id: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel(tag = id) { TransactionDetailScreenModel(id) }
        val transaction by model.transaction.collectAsState()

        // Gone once it was here means deleted; gone at the start is just the first read still running.
        var everLoaded by remember { mutableStateOf(false) }
        LaunchedEffect(transaction) {
            if (transaction != null) everLoaded = true else if (everLoaded) navigator.pop()
        }

        TransactionDetailScreenContent(
            transaction = transaction,
            use24Hour = rememberUse24HourClock(),
            onBack = navigator::pop,
            onClickEdit = { navigator.push(TransactionEditScreen(id)) },
            onDelete = model::delete,
        )
    }
}

/** Null [transaction] is the first read still running; the screen leaves once it was here and went. */
@Composable
fun TransactionDetailScreenContent(
    transaction: Transaction?,
    use24Hour: Boolean,
    onBack: () -> Unit = {},
    onClickEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(R.string.transaction),
                navigateUp = onBack,
                actions = if (transaction == null) {
                    emptyList()
                } else {
                    listOf(
                        AppBar.Action(
                            title = stringResource(R.string.action_edit),
                            icon = Icons.Outlined.Edit,
                            onClick = onClickEdit,
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
        if (transaction == null) {
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
            TransactionView(transaction = transaction, use24Hour = use24Hour)
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
                        onDelete()
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun TransactionView(
    transaction: Transaction,
    use24Hour: Boolean,
) {
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
    Field(stringResource(R.string.category), categoryLabel(transaction.category))
    Field(stringResource(R.string.description), transaction.description)
    Field(stringResource(R.string.merchant), transaction.merchant)
    HorizontalDivider()
    Field(stringResource(R.string.label_added), formatDate(transaction.createdAt, use24Hour))
    Field(stringResource(R.string.label_updated), formatDate(transaction.updatedAt, use24Hour))
    // Only when there is one. Updated says when anything last wrote to the row,
    // a re-parse included; this says the user did it, which is what makes the
    // row survive the next one.
    transaction.editedAt?.let {
        Field(stringResource(R.string.label_edited_by_you), formatDate(it, use24Hour))
    }
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

@Preview
@Composable
private fun TransactionDetailPreview() {
    AppTheme {
        TransactionDetailScreenContent(
            transaction = Transaction(
                accountId = "preview",
                sourceId = "dev.achmad.finbox.source.preview",
                emailMessageId = "message-1",
                index = 0,
                threadId = null,
                reference = "REF1",
                date = 1_700_000_000_000L,
                amount = 125_000,
                currency = "IDR",
                direction = TransactionDirection.OUTGOING,
                categoryName = TransactionCategory.FOOD.name,
                categorySource = CategorySource.RULE,
                description = "Coffee and a croissant",
                merchant = "Kopi Kenangan",
                createdAt = 1_700_000_000_000L,
                updatedAt = 1_700_000_000_000L,
                editedAt = null,
                deleted = false,
            ),
            use24Hour = true,
        )
    }
}

@Preview
@Composable
private fun TransactionDetailLoadingPreview() {
    AppTheme {
        TransactionDetailScreenContent(transaction = null, use24Hour = true)
    }
}
