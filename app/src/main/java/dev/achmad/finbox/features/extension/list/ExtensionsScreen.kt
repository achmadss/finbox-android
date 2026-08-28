package dev.achmad.finbox.features.extension.list

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import dev.achmad.finbox.core.extension.AvailableExtension
import dev.achmad.finbox.core.extension.rememberExtensionPainter
import dev.achmad.finbox.features.extension.detail.ExtensionDetailScreen
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.achmad.finbox.theme.AppTheme
import dev.achmad.data.model.InstalledExtension
import dev.achmad.finbox.R

object ExtensionsScreen : Screen {
    private fun readResolve(): Any = ExtensionsScreen

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { ExtensionsScreenModel() }
        val state by model.state.collectAsState()

        ExtensionsScreenContent(
            state = state,
            onBack = navigator::pop,
            onRefresh = model::refresh,
            onOpenExtension = { navigator.push(ExtensionDetailScreen(it)) },
            onInstall = model::install,
            onUpdate = model::update,
            onUpdateAll = model::updateAll,
            onCancelInstall = model::cancelInstall,
            onUninstall = model::uninstall,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreenContent(
    state: ExtensionsScreenModel.State,
    onBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onOpenExtension: (String) -> Unit = {},
    onInstall: (AvailableExtension) -> Unit = {},
    onUpdate: (String) -> Unit = {},
    onUpdateAll: () -> Unit = {},
    onCancelInstall: (String) -> Unit = {},
    onUninstall: (String) -> Unit = {},
) {
    var confirmUninstall by remember { mutableStateOf<ExtensionUiModel.Installed?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.dropShadow(RectangleShape, Shadow(3.dp)),
                title = { Text(stringResource(R.string.extensions)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }
                // A list, not a Box: the gesture needs something scrollable to
                // pull on, and an empty index is exactly when someone reaches for a refresh.
                state.isEmpty -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Box(Modifier.fillParentMaxSize(), Alignment.Center) {
                            Text(
                                stringResource(R.string.extensions_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                else -> ExtensionContent(
                    state = state,
                    onClickItem = { item ->
                        when (item) {
                            is ExtensionUiModel.Installed -> onOpenExtension(item.pkg)
                            is ExtensionUiModel.Available -> onInstall(item.extension)
                        }
                    },
                    onLongClickItem = { item ->
                        when (item) {
                            is ExtensionUiModel.Installed -> confirmUninstall = item
                            is ExtensionUiModel.Available -> onInstall(item.extension)
                        }
                    },
                    onClickAction = { item ->
                        when {
                            // Error keeps the row's action pointed at trying again.
                            item.installStep == InstallStep.Error -> when (item) {
                                is ExtensionUiModel.Installed -> onUpdate(item.pkg)
                                is ExtensionUiModel.Available -> onInstall(item.extension)
                            }
                            item is ExtensionUiModel.Installed ->
                                if (item.update != null) onUpdate(item.pkg)
                                else onOpenExtension(item.pkg)
                            item is ExtensionUiModel.Available -> onInstall(item.extension)
                        }
                    },
                    onClickCancel = { onCancelInstall(it.pkg) },
                    onClickUpdateAll = onUpdateAll,
                )
            }
        }
    }

    confirmUninstall?.let { item ->
        UninstallConfirmation(
            name = item.name,
            onConfirm = { onUninstall(item.pkg) },
            onDismiss = { confirmUninstall = null },
        )
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
                ExtensionHeader(stringResource(R.string.extensions_header_updates)) {
                    Button(onClick = onClickUpdateAll) { Text(stringResource(R.string.action_update_all)) }
                }
            }
            extensionItems(state.updates, onClickItem, onLongClickItem, onClickAction, onClickCancel)
        }

        if (state.installed.isNotEmpty()) {
            item(key = "header-installed", contentType = "header") { ExtensionHeader(stringResource(R.string.extensions_header_installed)) }
            extensionItems(state.installed, onClickItem, onLongClickItem, onClickAction, onClickCancel)
        }

        if (state.available.isNotEmpty()) {
            item(key = "header-available", contentType = "header") { ExtensionHeader(stringResource(R.string.extensions_header_available)) }
            extensionItems(state.available, onClickItem, onLongClickItem, onClickAction, onClickCancel)
        }

        if (state.errors.isNotEmpty()) {
            item(key = "header-errors", contentType = "header") { ExtensionHeader(stringResource(R.string.extensions_header_errors)) }
            items(state.errors.toList(), key = { "error-${it.first}" }) { (file, reason) ->
                Text(
                    text = stringResource(R.string.extension_load_error, file, reason),
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
    val version = stringResource(R.string.extension_version_name, item.versionName)
    val step = item.installStep.labelRes()?.let { stringResource(it) }
    val details = listOfNotNull(version, step)
    val warning = when {
        item.installStep == InstallStep.Error -> stringResource(R.string.extension_badge_failed)
        item is ExtensionUiModel.Installed && !item.enabled -> stringResource(R.string.extension_badge_disabled)
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
@StringRes
internal fun InstallStep.labelRes(): Int? = when (this) {
    InstallStep.Pending -> R.string.extension_step_pending
    InstallStep.Downloading -> R.string.extension_step_downloading
    InstallStep.Installing -> R.string.extension_step_installing
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
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel))
            }
        }
        item.installStep == InstallStep.Error -> {
            IconButton(onClick = { onClickAction(item) }) {
                Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.action_retry))
            }
        }
        item is ExtensionUiModel.Installed && item.update != null -> {
            IconButton(onClick = { onClickAction(item) }) {
                Icon(Icons.Outlined.GetApp, contentDescription = stringResource(R.string.action_update))
            }
        }
        item is ExtensionUiModel.Installed -> {
            IconButton(onClick = { onClickAction(item) }) {
                Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.label_extension_info))
            }
        }
        else -> {
            IconButton(onClick = { onClickAction(item) }) {
                Icon(Icons.Outlined.GetApp, contentDescription = stringResource(R.string.action_install))
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
        title = { Text(stringResource(R.string.uninstall_extension)) },
        text = { Text(stringResource(R.string.uninstall_extension_confirmation, name)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_uninstall)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Preview
@Composable
private fun ExtensionsScreenPreview() {
    AppTheme {
        ExtensionsScreenContent(
            state = ExtensionsScreenModel.State(
                isLoading = false,
                updates = listOf(
                    ExtensionUiModel.Installed(
                        extension = InstalledExtension(
                            pkg = "dev.achmad.extension.jago",
                            name = "Jago",
                            file = "/data/extensions/jago.apk",
                            versionCode = 3,
                            versionName = "1.2.0",
                            libVersion = "1.0",
                            sha256 = "0".repeat(64),
                            extensionIds = listOf("dev.achmad.finbox.extension.preview"),
                            enabled = true,
                        ),
                        update = AvailableExtension(
                            name = "Jago",
                            pkg = "dev.achmad.extension.jago",
                            versionCode = 4,
                            versionName = "1.3.0",
                            libVersion = 1.0,
                            apkUrl = "https://example.com/jago.apk",
                            sha256 = "0".repeat(64),
                            iconUrl = null,
                        ),
                        installStep = InstallStep.Idle,
                    ),
                ),
                installed = listOf(
                    ExtensionUiModel.Installed(
                        extension = InstalledExtension(
                            pkg = "dev.achmad.extension.bni",
                            name = "BNI",
                            file = "/data/extensions/bni.apk",
                            versionCode = 2,
                            versionName = "1.0.1",
                            libVersion = "1.0",
                            sha256 = "0".repeat(64),
                            extensionIds = listOf("dev.achmad.finbox.extension.preview2"),
                            enabled = true,
                        ),
                        update = null,
                        installStep = InstallStep.Idle,
                    ),
                ),
                available = listOf(
                    ExtensionUiModel.Available(
                        extension = AvailableExtension(
                            name = "BRI",
                            pkg = "dev.achmad.extension.bri",
                            versionCode = 5,
                            versionName = "2.0.0",
                            libVersion = 1.0,
                            apkUrl = "https://example.com/bri.apk",
                            sha256 = "0".repeat(64),
                            iconUrl = null,
                        ),
                        installStep = InstallStep.Idle,
                    ),
                ),
            ),
        )
    }
}

@Preview
@Composable
private fun ExtensionsScreenEmptyPreview() {
    AppTheme {
        ExtensionsScreenContent(state = ExtensionsScreenModel.State(isLoading = false))
    }
}
