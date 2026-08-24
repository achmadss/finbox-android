package dev.achmad.finbox.features.transaction.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionDirection
import dev.achmad.finbox.features.transaction.list.labelRes
import dev.achmad.finbox.parser.TransactionType
import dev.achmad.finbox.theme.components.AppBar
import dev.achmad.finbox.util.formatter.formatDateOnly
import dev.achmad.finbox.util.formatter.formatTime
import dev.achmad.finbox.util.ui.rememberUse24HourClock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.achmad.finbox.theme.AppTheme
import dev.achmad.finbox.util.preview.previewTransaction
import dev.achmad.finbox.R

/**
 * Edits one transaction. The ids, the timestamps and the deleted flag belong to the
 * parser and the database, so they stay out of it; currency and reference are editable
 * in the repository but not offered here.
 */
data class TransactionEditScreen(private val id: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel(tag = id) { TransactionDetailScreenModel(id) }
        val transaction by model.transaction.collectAsState()
        val types by model.types.collectAsState()

        // Seeded once, from the row as it was when this screen opened. Re-seeding on every
        // emission would throw away what is being typed the moment anything else writes.
        var draft by remember { mutableStateOf<Draft?>(null) }
        LaunchedEffect(transaction) {
            val current = transaction
            when {
                current != null && draft == null -> draft = current.toDraft()
                // Deleted while it was open — there is nothing left to edit.
                current == null && draft != null -> navigator.pop()
            }
        }

        TransactionEditScreenContent(
            draft = draft,
            types = types,
            use24Hour = rememberUse24HourClock(),
            // What is on screen against what is stored: the only thing worth warning about.
            dirty = draft != null && draft != transaction?.toDraft(),
            onDraftChange = { draft = it },
            onSave = { edited -> transaction?.let { model.save(edited.applyTo(it)) } },
            onLeave = navigator::pop,
        )
    }
}

/** Null [draft] is the first read still running. */
@Composable
internal fun TransactionEditScreenContent(
    draft: Draft?,
    types: List<TransactionType>,
    use24Hour: Boolean,
    dirty: Boolean = false,
    onDraftChange: (Draft) -> Unit = {},
    onSave: (Draft) -> Unit = {},
    onLeave: () -> Unit = {},
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    val leave: () -> Unit = { if (dirty) confirmDiscard = true else onLeave() }

    // Only while there is something to lose; otherwise back is back.
    BackHandler(enabled = dirty) { confirmDiscard = true }

    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(R.string.label_edit_transaction),
                navigateUp = leave,
                actions = listOfNotNull(
                    draft?.let {
                        AppBar.Action(
                            title = stringResource(R.string.action_save),
                            icon = Icons.Outlined.Check,
                            onClick = {
                                onSave(it)
                                onLeave()
                            },
                        )
                    },
                ),
            )
        },
    ) { padding ->
        if (draft == null) {
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
            TransactionEditor(
                draft = draft,
                types = types,
                use24Hour = use24Hour,
                onChange = onDraftChange,
            )
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_changes)) },
            text = { Text(stringResource(R.string.discard_changes_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        onLeave()
                    },
                ) { Text(stringResource(R.string.action_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.action_keep_editing)) }
            },
        )
    }
}

@Composable
private fun TransactionEditor(
    draft: Draft,
    types: List<TransactionType>,
    use24Hour: Boolean,
    onChange: (Draft) -> Unit,
) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickType by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransactionDirection.entries.forEach { direction ->
                FilterChip(
                    selected = draft.direction == direction,
                    // Tapping the selected one clears it, which is what an unparsed direction looks like.
                    onClick = { onChange(draft.copy(direction = direction.takeIf { it != draft.direction })) },
                    label = { Text(stringResource(direction.labelRes)) },
                )
            }
        }
        OutlinedTextField(
            value = draft.amount,
            onValueChange = { onChange(draft.copy(amount = it.filter(Char::isDigit))) },
            label = { Text(stringResource(R.string.amount)) },
            // Unsigned: the direction decides which way the money went.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PickerField(
                label = stringResource(R.string.date),
                value = draft.date?.let { formatDateOnly(it) } ?: stringResource(R.string.not_set),
                modifier = Modifier.weight(1f),
                onClick = { pickDate = true },
            )
            PickerField(
                label = stringResource(R.string.time),
                // No date, no time to set: the row has no instant to hang one on.
                value = draft.date?.let { formatTime(it, use24Hour) } ?: "-",
                modifier = Modifier.weight(1f),
                enabled = draft.date != null,
                onClick = { pickTime = true },
            )
        }
        PickerField(
            label = stringResource(R.string.type),
            value = types.nameOf(draft.type) ?: draft.type ?: stringResource(R.string.none),
            modifier = Modifier.fillMaxWidth(),
            onClick = { pickType = true },
        )
        OutlinedTextField(
            value = draft.category,
            onValueChange = { onChange(draft.copy(category = it)) },
            label = { Text(stringResource(R.string.category)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.description,
            onValueChange = { onChange(draft.copy(description = it)) },
            label = { Text(stringResource(R.string.description)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.merchant,
            onValueChange = { onChange(draft.copy(merchant = it)) },
            label = { Text(stringResource(R.string.merchant)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (pickDate) {
        DatePickerSheet(
            date = draft.date,
            onDismiss = { pickDate = false },
            onSelect = { onChange(draft.copy(date = it)) },
        )
    }

    if (pickTime && draft.date != null) {
        TimePickerDialog(
            date = draft.date,
            use24Hour = use24Hour,
            onDismiss = { pickTime = false },
            onSelect = { onChange(draft.copy(date = it)) },
        )
    }

    if (pickType) {
        TypePickerDialog(
            selected = draft.type,
            types = types,
            onDismiss = { pickType = false },
            onSelect = { onChange(draft.copy(type = it)) },
        )
    }
}

/** A read-only value that opens a picker. Weighted, so a pair of them share a row. */
@Composable
private fun PickerField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedCard(onClick = onClick, enabled = enabled, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = value)
        }
    }
}

/** Keeps the time of day, so picking a day does not silently move the transaction to midnight. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(date: Long?, onDismiss: () -> Unit, onSelect: (Long?) -> Unit) {
    // The picker works in UTC days while the row holds an instant in the device's zone, so
    // the day goes out and comes back through UTC.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = date?.toLocalDate()?.atStartOfDay(ZoneOffset.UTC)?.toInstant()
            ?.toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val day = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                        val time = date?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() }
                            ?: LocalTime.MIDNIGHT
                        onSelect(day.atTime(time).toEpochMillis())
                    }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            // A row the parser found no date for is a real state, so it has to be reachable.
            TextButton(
                onClick = {
                    onSelect(null)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_clear)) }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    date: Long,
    use24Hour: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    val at = remember(date) { Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()) }
    val state = rememberTimePickerState(
        initialHour = at.hour,
        initialMinute = at.minute,
        is24Hour = use24Hour,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(
                onClick = {
                    onSelect(at.toLocalDate().atTime(state.hour, state.minute).toEpochMillis())
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * The types the row's parser declares, plus None. A type the parser dropped stays in the
 * list while it is the one selected, so opening the picker cannot quietly discard it.
 */
@Composable
private fun TypePickerDialog(
    selected: String?,
    types: List<TransactionType>,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    val none = stringResource(R.string.none)
    val options = buildList<Pair<String?, String>> {
        add(null to none)
        types.forEach { add(it.key to it.name) }
        if (selected != null && types.none { it.key == selected }) add(selected to selected)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.type)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (key, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(key)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = key == selected, onClick = null)
                        Text(text = name, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private fun LocalDateTime.toEpochMillis(): Long =
    atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** The parser's name for a stored type key, or null when nothing declares it. */
internal fun List<TransactionType>.nameOf(key: String?): String? =
    key?.let { stored -> firstOrNull { it.key == stored }?.name }

/** The form's state: text while it is being typed, parsed back into a [Transaction] on save. */
internal data class Draft(
    val date: Long?,
    val amount: String,
    val direction: TransactionDirection?,
    val type: String?,
    val category: String,
    val description: String,
    val merchant: String,
)

internal fun Transaction.toDraft() = Draft(
    date = date,
    amount = amount?.toString().orEmpty(),
    direction = direction,
    type = type,
    category = category.orEmpty(),
    description = description.orEmpty(),
    merchant = merchant.orEmpty(),
)

/**
 * Blank means "the parser found nothing", i.e. null — the same as an untouched row.
 * Fields the form does not offer keep whatever the row already holds.
 */
internal fun Draft.applyTo(transaction: Transaction) = transaction.copy(
    date = date,
    amount = amount.trim().toLongOrNull(),
    direction = direction,
    type = type,
    category = category.blankToNull(),
    description = description.blankToNull(),
    merchant = merchant.blankToNull(),
)

private fun String.blankToNull(): String? = trim().takeIf { it.isNotEmpty() }

@Preview
@Composable
private fun TransactionEditPreview() {
    AppTheme {
        TransactionEditScreenContent(
            draft = previewTransaction().toDraft(),
            types = emptyList(),
            use24Hour = true,
        )
    }
}
