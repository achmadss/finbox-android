package dev.achmad.finbox.theme.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.achmad.finbox.util.preference.collectAsState
import kotlinx.coroutines.launch

/**
 * Renders the [Preference] items a settings screen describes.
 *
 * A disabled item is left out rather than greyed: settings that do not apply —
 * a schedule's constraints while the schedule is off — are noise, not choices.
 */
@Composable
fun PreferenceItems(
    items: List<Preference>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
    ) {
        items.forEachIndexed { index, preference ->
            when (preference) {
                is Preference.PreferenceGroup -> {
                    if (!preference.enabled) return@forEachIndexed
                    item { PreferenceGroupHeader(title = preference.title) }
                    items(preference.preferenceItems.filter { it.enabled }) { PreferenceItemRow(it) }
                    if (index < items.lastIndex) {
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }
                }
                is Preference.PreferenceItem -> {
                    if (preference.enabled) item { PreferenceItemRow(preference) }
                }
            }
        }
    }
}

/** A whole settings screen: the app bar, a back arrow, and the items. */
@Composable
fun PreferenceScreen(
    title: String,
    onBackPressed: (() -> Unit)? = null,
    itemsProvider: @Composable () -> List<Preference>,
) {
    Scaffold(
        topBar = { AppBar(title = title, navigateUp = onBackPressed) },
    ) { contentPadding ->
        PreferenceItems(
            items = itemsProvider(),
            contentPadding = contentPadding,
        )
    }
}

@Composable
private fun PreferenceItemRow(item: Preference.PreferenceItem) {
    val scope = rememberCoroutineScope()
    when (item) {
        is Preference.PreferenceItem.TextPreference -> {
            TextPreferenceWidget(
                title = item.title,
                subtitle = item.subtitle,
                icon = item.icon,
                onPreferenceClick = item.onClick,
            )
        }
        is Preference.PreferenceItem.SwitchPreference -> {
            val checked by item.preference.collectAsState()
            SwitchPreferenceWidget(
                title = item.title,
                subtitle = item.subtitle,
                icon = item.icon,
                checked = checked,
                onCheckedChanged = { value ->
                    item.preference.set(value)
                    scope.launch { item.onValueChanged(value) }
                },
            )
        }
        is Preference.PreferenceItem.ListPreference<*> -> {
            val value by item.preference.collectAsState()
            ListPreferenceWidget(
                value = value,
                title = item.title,
                subtitle = item.subtitle?.format(item.internalEntryOf(value)),
                icon = item.icon,
                entries = item.entries,
                onValueChange = { selected ->
                    item.internalSet(selected)
                    scope.launch { item.internalOnValueChanged(selected) }
                },
            )
        }
        is Preference.PreferenceItem.CheckPreference -> {
            CheckPreferenceWidget(
                title = item.title,
                subtitle = item.subtitle,
                icon = item.icon,
                checked = item.checked,
                onClick = { item.onClick(item.value) },
            )
        }
        is Preference.PreferenceItem.InfoPreference -> InfoWidget(text = item.title)
    }
}
