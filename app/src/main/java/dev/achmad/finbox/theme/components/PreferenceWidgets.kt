package dev.achmad.finbox.theme.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import dev.achmad.finbox.R

/** The rows [PreferenceScreen] is made of, in the style of Tachiyomi's settings. */
internal val PrefsHorizontalPadding = 16.dp
internal val PrefsVerticalPadding = 16.dp
private val TitleFontSize = 16.sp
private val TrailingWidgetBuffer = 16.dp
private val MinRowHeight = 56.dp

@Composable
private fun BasePreferenceWidget(
    modifier: Modifier = Modifier,
    title: String? = null,
    subcomponent: @Composable (ColumnScope.() -> Unit)? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    widget: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .sizeIn(minHeight = MinRowHeight)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(modifier = Modifier.padding(start = PrefsHorizontalPadding, end = 8.dp)) {
                Icon(
                    imageVector = icon,
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = null,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = PrefsVerticalPadding),
        ) {
            if (!title.isNullOrBlank()) {
                Text(
                    modifier = Modifier.padding(horizontal = PrefsHorizontalPadding),
                    text = title,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = TitleFontSize,
                )
            }
            subcomponent?.invoke(this)
        }
        if (widget != null) {
            Box(modifier = Modifier.padding(end = PrefsHorizontalPadding)) { widget() }
        }
    }
}

@Composable
fun TextPreferenceWidget(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    widget: @Composable (() -> Unit)? = null,
    onPreferenceClick: (() -> Unit)? = null,
) {
    BasePreferenceWidget(
        modifier = modifier,
        title = title,
        subcomponent = subtitle?.takeIf { it.isNotBlank() }?.let {
            {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = PrefsHorizontalPadding),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 10,
                )
            }
        },
        icon = icon,
        onClick = onPreferenceClick,
        widget = widget,
    )
}

@Composable
fun SwitchPreferenceWidget(
    title: String,
    checked: Boolean,
    onCheckedChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    TextPreferenceWidget(
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        icon = icon,
        widget = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.padding(start = TrailingWidgetBuffer),
            )
        },
        onPreferenceClick = { onCheckedChanged(!checked) },
    )
}

@Composable
fun <T> ListPreferenceWidget(
    value: T,
    title: String,
    entries: Map<out T, String>,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    var showDialog by remember { mutableStateOf(false) }

    TextPreferenceWidget(
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        icon = icon,
        onPreferenceClick = { showDialog = true },
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = title) },
            text = {
                LazyColumn {
                    entries.forEach { entry ->
                        item {
                            DialogRow(
                                label = entry.value,
                                selected = value == entry.key,
                                onSelect = {
                                    onValueChange(entry.key)
                                    showDialog = false
                                },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text(text = stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun DialogRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, onClick = { if (!selected) onSelect() })
            .fillMaxWidth()
            .minimumInteractiveComponentSize(),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 24.dp),
        )
    }
}

@Composable
fun CheckPreferenceWidget(
    title: String,
    checked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    TextPreferenceWidget(
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        icon = icon,
        widget = {
            if (checked) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = TrailingWidgetBuffer),
                )
            }
        },
        onPreferenceClick = onClick,
    )
}

@Composable
fun PreferenceGroupHeader(title: String) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp, top = 14.dp),
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(horizontal = PrefsHorizontalPadding),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun InfoWidget(text: String) {
    Column(
        modifier = Modifier.padding(horizontal = PrefsHorizontalPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
