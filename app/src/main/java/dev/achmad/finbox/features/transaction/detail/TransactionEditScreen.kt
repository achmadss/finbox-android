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
import dev.achmad.data.model.CategorySource
import dev.achmad.data.model.TransactionCategory
import dev.achmad.data.model.signature
import dev.achmad.finbox.features.transaction.categoryLabel
import dev.achmad.finbox.features.transaction.pickableCategories
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionDirection
import dev.achmad.finbox.features.transaction.list.labelRes
import dev.achmad.finbox.extension.TransactionMethod
import dev.achmad.finbox.theme.components.AppBar
import dev.achmad.finbox.util.formatter.formatDateOnly
import dev.achmad.finbox.util.formatter.formatTime
import dev.achmad.finbox.util.ui.rememberUse24HourClock
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.achmad.finbox.theme.AppTheme
import dev.achmad.finbox.R

/**
 * Edits one transaction. The ids, timestamps, and the deleted flag belong to the
 * extension and the database, so they stay out of the form; currency and reference
 * are editable but not offered here.
 */
data class TransactionEditScreen(private val id: String) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel(tag = id) { TransactionDetailScreenModel(id) }
        val transaction by model.transaction.collectAsState()
        val methods by model.methods.collectAsState()

        // Seeded once, from the row as it was when this screen opened; re-seeding on
        // every emission would throw away what is being typed.
        var draft by remember { mutableStateOf<Draft?>(null) }
        LaunchedEffect(transaction) {
            val current = transaction
            when {
                current != null && draft == null -> draft = current.toDraft()
                // Deleted while it was open — there is nothing left to edit.
                current == null && draft != null -> navigator.pop()
            }
        }

        val scope = rememberCoroutineScope()
        var offer by remember { mutableStateOf<SimilarCategoryOffer?>(null) }

        TransactionEditScreenContent(
            draft = draft,
            methods = methods,
            use24Hour = rememberUse24HourClock(),
            // What is on screen against what is stored: the only thing worth warning about.
            dirty = draft != null && draft != transaction?.toDraft(),
            onDraftChange = { draft = it },
            onSave = { edited ->
                transaction?.let { current ->
                    val updated = edited.applyTo(current)
                    model.save(updated)
                    val category = updated.category
                    // Filing this row is only half of it. The cache carries the
                    // decision forward to rows parsed later, and nothing carries
                    // it back, so the rows already sitting there get asked about.
                    if (category != null && category != current.category) {
                        scope.launch {
                            val similar = model.similarTo(updated)
                            if (similar.isEmpty()) {
                                navigator.pop()
                            } else {
                                offer = SimilarCategoryOffer(category, similar.map { it.id })
                            }
                        }
                    } else {
                        navigator.pop()
                    }
                }
            },
            onLeave = navigator::pop,
        )

        offer?.let { pending ->
            // Leaves either way: the edit is already saved, and this is an offer
            // about other rows rather than a step in saving this one.
            val leave: () -> Unit = {
                offer = null
                navigator.pop()
            }
            AlertDialog(
                onDismissRequest = leave,
                title = { Text(stringResource(R.string.apply_to_similar_title)) },
                text = {
                    Text(
                        pluralStringResource(
                            R.plurals.apply_to_similar_message,
                            pending.ids.size,
                            pending.ids.size,
                            categoryLabel(pending.category),
                        ),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            model.applyCategoryTo(pending.ids, pending.category)
                            leave()
                        },
                    ) { Text(stringResource(R.string.action_apply_to_similar)) }
                },
                dismissButton = {
                    TextButton(onClick = leave) { Text(stringResource(R.string.action_only_this_one)) }
                },
            )
        }
    }
}

private data class SimilarCategoryOffer(
    val category: TransactionCategory,
    val ids: List<String>,
)

/** Null [draft] is the first read still running. */
@Composable
internal fun TransactionEditScreenContent(
    draft: Draft?,
    methods: List<TransactionMethod>,
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
                            onClick = { onSave(it) },
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
                methods = methods,
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
    methods: List<TransactionMethod>,
    use24Hour: Boolean,
    onChange: (Draft) -> Unit,
) {
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickMethod by remember { mutableStateOf(false) }
    var pickCategory by remember { mutableStateOf(false) }

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
            label = stringResource(R.string.method),
            value = methods.nameOf(draft.method) ?: draft.method ?: stringResource(R.string.none),
            modifier = Modifier.fillMaxWidth(),
            onClick = { pickMethod = true },
        )
        PickerField(
            label = stringResource(R.string.category),
            value = categoryLabel(draft.category),
            modifier = Modifier.fillMaxWidth(),
            onClick = { pickCategory = true },
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

    if (pickMethod) {
        MethodPickerDialog(
            selected = draft.method,
            methods = methods,
            onDismiss = { pickMethod = false },
            onSelect = { onChange(draft.copy(method = it)) },
        )
    }

    if (pickCategory) {
        CategoryPickerDialog(
            selected = draft.category,
            onDismiss = { pickCategory = false },
            onSelect = { onChange(draft.copy(category = it)) },
        )
    }
}

/**
 * The app's categories, plus Uncategorized. It clears the row back to
 * unprocessed, which is what hands it to the next classify pass; UNKNOWN is
 * missing on purpose, since code assigns that after looking.
 */
@Composable
internal fun CategoryPickerDialog(
    selected: TransactionCategory?,
    onDismiss: () -> Unit,
    onSelect: (TransactionCategory?) -> Unit,
    includeUncategorized: Boolean = true,
) {
    val options = if (includeUncategorized) {
        listOf<TransactionCategory?>(null) + pickableCategories
    } else {
        pickableCategories
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.category)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(category)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = category == selected, onClick = null)
                        Text(
                            text = categoryLabel(category),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

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
            // A row the extension found no date for is a real state, so it has to be reachable.
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
 * The methods the row's extension declares, plus None. A method the extension dropped stays in the
 * list while it is the one selected, so opening the picker cannot quietly discard it.
 */
@Composable
private fun MethodPickerDialog(
    selected: String?,
    methods: List<TransactionMethod>,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    val none = stringResource(R.string.none)
    val options = buildList<Pair<String?, String>> {
        add(null to none)
        methods.forEach { add(it.key to it.name) }
        if (selected != null && methods.none { it.key == selected }) add(selected to selected)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.method)) },
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

internal fun List<TransactionMethod>.nameOf(key: String?): String? =
    key?.let { stored -> firstOrNull { it.key == stored }?.name }

/** The form's state: text while it is being typed, parsed back into a [Transaction] on save. */
internal data class Draft(
    val date: Long?,
    val amount: String,
    val direction: TransactionDirection?,
    val method: String?,
    val category: TransactionCategory?,
    val description: String,
    val merchant: String,
)

internal fun Transaction.toDraft() = Draft(
    date = date,
    amount = amount?.toString().orEmpty(),
    direction = direction,
    method = method,
    category = category,
    description = description.orEmpty(),
    merchant = merchant.orEmpty(),
)

/**
 * Blank means "the extension found nothing", i.e. null — the same as an untouched row.
 * Fields the form does not offer keep whatever the row already holds.
 */
internal fun Draft.applyTo(transaction: Transaction): Transaction {
    val edited = transaction.copy(
        date = date,
        amount = amount.trim().toLongOrNull(),
        direction = direction,
        method = method,
        description = description.blankToNull(),
        merchant = merchant.blankToNull(),
    )
    return when {
        // Filing it yourself makes it yours, and a classify pass then leaves it
        // alone unless it is told outright to replace manual work.
        category != transaction.category -> edited.copy(
            categoryName = category?.name,
            categorySource = category?.let { CategorySource.USER },
        )
        // UNKNOWN means the receipt did not say what this was for. An edit that
        // changed what a classifier would read makes that answer stale, so hand
        // the row back to the next pass. Comparing signatures, not asking whether
        // one is complete: an amount change alters nothing a classifier looks at.
        transaction.category == TransactionCategory.UNKNOWN &&
            edited.signature() != transaction.signature() ->
            edited.copy(categoryName = null, categorySource = null)
        else -> edited
    }
}

private fun String.blankToNull(): String? = trim().takeIf { it.isNotEmpty() }

@Preview
@Composable
private fun TransactionEditPreview() {
    AppTheme {
        TransactionEditScreenContent(
            draft = Transaction(
                accountId = "preview",
                extensionId = 1L,
                emailMessageId = "message-1",
                index = 0,
                threadId = null,
                reference = "REF1",
                date = 1_700_000_000_000L,
                amount = 125_000,
                currency = "IDR",
                direction = TransactionDirection.OUTGOING,
                method = "QRIS",
                categoryName = TransactionCategory.FOOD.name,
                categorySource = CategorySource.AI,
                description = "Coffee and a croissant",
                merchant = "Kopi Kenangan",
                createdAt = 1_700_000_000_000L,
                updatedAt = 1_700_000_000_000L,
                editedAt = null,
                deleted = false,
            ).toDraft(),
            methods = emptyList(),
            use24Hour = true,
        )
    }
}
