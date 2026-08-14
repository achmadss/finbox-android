package dev.achmad.finbox.features.extensions

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import dev.achmad.finbox.core.extension.InstallStep
import dev.achmad.finbox.core.extension.rememberExtensionPainter

object ExtensionsScreen : Screen {
    private fun readResolve(): Any = ExtensionsScreen

    override val key: ScreenKey = uniqueScreenKey

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { ExtensionsScreenModel() }
        val state by model.state.collectAsState()
        var confirmUninstall by remember { mutableStateOf<ExtensionUiModel.Installed?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(
                    modifier = Modifier.dropShadow(RectangleShape, Shadow(3.dp)),
                    title = { Text("Extensions") },
                    actions = {
                        IconButton(onClick = model::refresh, enabled = !state.isRefreshing) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                        }
                    },
                )
            },
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = model::refresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.isEmpty -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(
                            "Nothing to show. Pull to refresh.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> ExtensionContent(
                        state = state,
                        onClickItem = { item ->
                            when (item) {
                                is ExtensionUiModel.Installed -> navigator.push(ExtensionDetailsScreen(item.pkg))
                                is ExtensionUiModel.Available -> model.install(item.extension)
                            }
                        },
                        onLongClickItem = { item ->
                            when (item) {
                                is ExtensionUiModel.Installed -> confirmUninstall = item
                                is ExtensionUiModel.Available -> model.install(item.extension)
                            }
                        },
                        onClickAction = { item ->
                            when {
                                // Error keeps the row's action pointed at trying again.
                                item.installStep == InstallStep.Error -> when (item) {
                                    is ExtensionUiModel.Installed -> model.update(item.pkg)
                                    is ExtensionUiModel.Available -> model.install(item.extension)
                                }
                                item is ExtensionUiModel.Installed ->
                                    if (item.update != null) model.update(item.pkg)
                                    else navigator.push(ExtensionDetailsScreen(item.pkg))
                                item is ExtensionUiModel.Available -> model.install(item.extension)
                            }
                        },
                        onClickCancel = { model.cancelInstall(it.pkg) },
                        onClickUpdateAll = model::updateAll,
                    )
                }
            }
        }

        confirmUninstall?.let { item ->
            UninstallConfirmation(
                name = item.name,
                onConfirm = { model.uninstall(item.pkg) },
                onDismiss = { confirmUninstall = null },
            )
        }
    }
}

@Composable
private fun ExtensionContent(
    state: ExtensionsScreenModel.State,
    onClickItem: (ExtensionUiModel) -> Unit,
    onLongClickItem: (ExtensionUiModel) -> Unit,
    onClickAction: (ExtensionUiModel) -> Unit,
    onClickCancel: (ExtensionUiModel) -> Unit,
    onClickUpdateAll: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        if (state.updates.isNotEmpty()) {
            item(key = "header-updates", contentType = "header") {
                ExtensionHeader("Update pending") {
                    Button(onClick = onClickUpdateAll) { Text("Update all") }
                }
            }
            extensionItems(state.updates, onClickItem, onLongClickItem, onClickAction, onClickCancel)
        }

        if (state.installed.isNotEmpty()) {
            item(key = "header-installed", contentType = "header") { ExtensionHeader("Installed") }
            extensionItems(state.installed, onClickItem, onLongClickItem, onClickAction, onClickCancel)
        }

        if (state.available.isNotEmpty()) {
            item(key = "header-available", contentType = "header") { ExtensionHeader("Available") }
            extensionItems(state.available, onClickItem, onLongClickItem, onClickAction, onClickCancel)
        }

        if (state.errors.isNotEmpty()) {
            item(key = "header-errors", contentType = "header") { ExtensionHeader("Failed to load") }
            items(state.errors.toList(), key = { "error-${it.first}" }) { (file, reason) ->
                Text(
                    text = "$file: $reason",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun LazyListScope.extensionItems(
    items: List<ExtensionUiModel>,
    onClickItem: (ExtensionUiModel) -> Unit,
    onLongClickItem: (ExtensionUiModel) -> Unit,
    onClickAction: (ExtensionUiModel) -> Unit,
    onClickCancel: (ExtensionUiModel) -> Unit,
) = items(
    items = items,
    key = { "extension-${it.pkg}" },
    contentType = { "item" },
) { item ->
    ExtensionItem(
        modifier = Modifier.animateItem(),
        item = item,
        onClickItem = onClickItem,
        onLongClickItem = onLongClickItem,
        onClickAction = onClickAction,
        onClickCancel = onClickCancel,
    )
}

@Composable
private fun ExtensionItem(
    item: ExtensionUiModel,
    onClickItem: (ExtensionUiModel) -> Unit,
    onLongClickItem: (ExtensionUiModel) -> Unit,
    onClickAction: (ExtensionUiModel) -> Unit,
    onClickCancel: (ExtensionUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClickItem(item) },
                onLongClick = { onLongClickItem(item) },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (item.isRunning) {
                CircularProgressIndicator(Modifier.size(40.dp), strokeWidth = 2.dp)
            }
            // Pulls the icon in while the ring around it spins.
            val inset by animateDpAsState(if (item.isRunning) 8.dp else 0.dp, label = "icon inset")
            ExtensionIcon(item, Modifier.matchParentSize().padding(inset))
        }

        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                text = item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            ExtensionSubtitle(item)
        }

        ExtensionItemActions(item, onClickAction, onClickCancel)
    }
}

@Composable
private fun ExtensionSubtitle(item: ExtensionUiModel) {
    val details = buildList {
        add("v${item.versionName}")
        item.installStep.label()?.let { add(it) }
    }
    val warning = when {
        item.installStep == InstallStep.Error -> "FAILED"
        item is ExtensionUiModel.Installed && !item.enabled -> "DISABLED"
        else -> null
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = details.joinToString(" • "),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (warning != null) {
            Text(
                text = " • $warning",
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Only the steps worth naming: the finished ones are told by the row itself. */
private fun InstallStep.label(): String? = when (this) {
    InstallStep.Pending -> "Pending"
    InstallStep.Downloading -> "Downloading"
    InstallStep.Installing -> "Installing"
    else -> null
}

@Composable
private fun ExtensionItemActions(
    item: ExtensionUiModel,
    onClickAction: (ExtensionUiModel) -> Unit,
    onClickCancel: (ExtensionUiModel) -> Unit,
) {
    when {
        item.isRunning -> {
            IconButton(onClick = { onClickCancel(item) }) {
                Icon(Icons.Outlined.Close, contentDescription = "Cancel")
            }
        }
        item.installStep == InstallStep.Error -> {
            IconButton(onClick = { onClickAction(item) }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Retry")
            }
        }
        item is ExtensionUiModel.Installed && item.update != null -> {
            IconButton(onClick = { onClickAction(item) }) {
                Icon(Icons.Outlined.GetApp, contentDescription = "Update")
            }
        }
        item is ExtensionUiModel.Installed -> {
            IconButton(onClick = { onClickAction(item) }) {
                Icon(Icons.Outlined.Settings, contentDescription = "Extension info")
            }
        }
        else -> {
            IconButton(onClick = { onClickAction(item) }) {
                Icon(Icons.Outlined.GetApp, contentDescription = "Install")
            }
        }
    }
}

@Composable
private fun ExtensionHeader(
    text: String,
    action: @Composable () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        action()
    }
}

@Composable
fun ExtensionIcon(
    item: ExtensionUiModel,
    modifier: Modifier = Modifier,
) {
    val shaped = modifier.clip(MaterialTheme.shapes.extraSmall)
    when (item) {
        is ExtensionUiModel.Installed -> Image(
            painter = rememberExtensionPainter(item.extension),
            contentDescription = null,
            modifier = shaped,
        )
        is ExtensionUiModel.Available -> when (val url = item.extension.iconUrl) {
            null -> Icon(Icons.Filled.Extension, contentDescription = null, modifier = shaped)
            else -> AsyncImage(model = url, contentDescription = null, modifier = shaped)
        }
    }
}

@Composable
fun UninstallConfirmation(
    name: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
        title = { Text("Uninstall extension") },
        text = { Text("Remove $name? Transactions it already parsed stay.") },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) { Text("Uninstall") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
