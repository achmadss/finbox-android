package dev.achmad.finbox.features.rules

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.achmad.data.model.CategoryRule
import dev.achmad.data.model.TransactionCategory
import dev.achmad.data.model.TransactionDirection
import dev.achmad.finbox.R
import dev.achmad.finbox.features.transaction.categoryLabel
import dev.achmad.finbox.features.transaction.detail.CategoryPickerDialog
import dev.achmad.finbox.features.transaction.list.labelRes
import dev.achmad.finbox.theme.components.AppBar

/**
 * The rules, one line each; tap to edit, delete button, and a way to add.
 *
 * Declarations also come free with group filing; this is where they are seen
 * and changed. Editing is the only path to "replace existing rows" because it
 * is the only one that touches decisions already made — and it shows the count
 * before running.
 */
object CategoryRulesScreen : Screen {
    private fun readResolve(): Any = CategoryRulesScreen

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { CategoryRulesScreenModel() }
        val rules by model.rules.collectAsState()
        var editing by remember { mutableStateOf<CategoryRule?>(null) }
        var adding by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(R.string.rules_title),
                    navigateUp = navigator::pop,
                )
            },
        ) { padding ->
            if (rules.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.rules_empty),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.rules_empty_info),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { adding = true }) {
                        Text(stringResource(R.string.rules_add))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(rules, key = { it.id }) { rule ->
                        RuleRow(
                            rule = rule,
                            onEdit = { editing = rule },
                            onDelete = { model.delete(rule) },
                        )
                    }
                    item {
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { adding = true },
                        ) { Text(stringResource(R.string.rules_add)) }
                    }
                }
            }
        }

        if (adding) {
            RuleEditorDialog(
                existing = null,
                model = model,
                onDismiss = { adding = false },
                onSave = { merchant, direction, category, replaceExisting ->
                    model.save(merchant, direction, category, replaceExisting)
                    adding = false
                },
            )
        }
        editing?.let { existing ->
            RuleEditorDialog(
                existing = existing,
                model = model,
                onDismiss = { editing = null },
                onSave = { merchant, direction, category, replaceExisting ->
                    model.save(merchant, direction, category, replaceExisting)
                    editing = null
                },
            )
        }
    }
}

@Composable
private fun RuleRow(rule: CategoryRule, onEdit: () -> Unit, onDelete: () -> Unit) {
    var confirmingDelete by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.merchant,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = buildList {
                    rule.direction?.let { add(stringResource(it.labelRes)) }
                    add(categoryLabel(rule.category))
                }.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { confirmingDelete = true }) {
            Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            text = {
                Text(stringResource(R.string.rules_delete_confirm, rule.merchant))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmingDelete = false },
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/**
 * Add or edit a rule.
 *
 * Adding saves directly. Editing asks one more question — future only, or
 * existing rows too — with the count of what the replacement would touch,
 * because that is the only path that overwrites decisions already made.
 */
@Composable
private fun RuleEditorDialog(
    existing: CategoryRule?,
    model: CategoryRulesScreenModel,
    onDismiss: () -> Unit,
    onSave: (merchant: String, direction: TransactionDirection?, category: TransactionCategory, replaceExisting: Boolean) -> Unit,
) {
    var merchant by remember { mutableStateOf(TextFieldValue(existing?.merchant ?: "")) }
    var direction by remember { mutableStateOf<TransactionDirection?>(existing?.direction) }
    var category by remember { mutableStateOf<TransactionCategory?>(existing?.category) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var askScope by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.rules_add else R.string.rules_edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text(stringResource(R.string.merchant)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DirectionPicker(direction = direction, onSelect = { direction = it })
                // Reads like a field, opens like a picker: the category text
                // with a chevron, tap to open the same dialog every other
                // category pick in the app uses.
                OutlinedField(
                    value = categoryLabel(category),
                    onClick = { showCategoryPicker = true },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = merchant.text.isNotBlank() && category != null,
                onClick = {
                    if (existing == null) {
                        onSave(merchant.text, direction, category!!, false)
                    } else {
                        askScope = true
                    }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    if (showCategoryPicker) {
        CategoryPickerDialog(
            selected = category,
            onDismiss = { showCategoryPicker = false },
            onSelect = { category = it ?: category },
            includeUncategorized = false,
        )
    }

    if (askScope) {
        var count by remember { mutableStateOf<Int?>(null) }
        var replaceChoice by remember { mutableStateOf(false) }
        LaunchedEffect(merchant.text, direction) {
            count = null
            if (merchant.text.isNotBlank()) {
                count = model.countReplace(merchant.text, direction) ?: 0
            }
        }
        AlertDialog(
            onDismissRequest = { askScope = false },
            title = { Text(stringResource(R.string.rules_apply_scope_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.rules_apply_scope_info, merchant.text),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { replaceChoice = false },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = !replaceChoice, onClick = { replaceChoice = false })
                        Text(stringResource(R.string.rules_apply_future_only))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { replaceChoice = true },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = replaceChoice, onClick = { replaceChoice = true })
                        Text(stringResource(R.string.rules_apply_existing_too))
                    }
                    if (replaceChoice && count != null) {
                        Text(
                            text = stringResource(R.string.rules_apply_replace_count, count!!),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !replaceChoice || count != null,
                    onClick = { onSave(merchant.text, direction, category!!, replaceChoice) },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { askScope = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * A field-shaped control: text on the left, chevron-down on the right, tap
 * anywhere to act. Used where "pick one of a short list" still looks like a
 * form field but a dropdown menu would be wrong — a category picks from a
 * dialog, not a menu.
 */
@Composable
private fun OutlinedField(
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
        Icon(
            imageVector = Icons.Outlined.ArrowDropDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Direction as chips: one tap, one of three. A rule defaults to "any" till a
 * direction is picked — that is the rule that would govern the whole merchant.
 */
@Composable
private fun DirectionPicker(
    direction: TransactionDirection?,
    onSelect: (TransactionDirection?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = direction == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.rules_any_direction)) },
        )
        TransactionDirection.entries.forEach { option ->
            FilterChip(
                selected = direction == option,
                onClick = { onSelect(option) },
                label = { Text(stringResource(option.labelRes)) },
            )
        }
    }
}
