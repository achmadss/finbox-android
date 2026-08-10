package dev.achmad.finbox.screens.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import dev.achmad.domain.model.InstalledExtension
import dev.achmad.finbox.extension.AvailableExtension
import dev.achmad.finbox.extension.ExtensionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object ExtensionsScreen : Screen, KoinComponent {

    override val key: ScreenKey = uniqueScreenKey

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val model = rememberScreenModel { ExtensionsScreenModel() }
        val installed by model.installed.collectAsState()
        val available by model.available.collectAsState()
        val errors by model.errors.collectAsState()
        val busy by model.busy.collectAsState()

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Extensions") },
                    actions = {
                        IconButton(onClick = model::refresh, enabled = !busy) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text("Installed", style = MaterialTheme.typography.titleMedium)
                }
                items(installed, key = { it.pkg }) { extension ->
                    InstalledCard(
                        extension = extension,
                        onToggle = { model.setEnabled(extension.pkg, it) },
                        onRemove = { model.remove(extension.pkg) },
                        updateAvailable = available.firstOrNull { it.pkg == extension.pkg && it.versionCode > extension.versionCode },
                        onUpdate = { model.update(extension.pkg) },
                    )
                }
                item {
                    Text(
                        "Available",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                items(available.filter { av -> installed.none { it.pkg == av.pkg } }, key = { it.pkg }) { av ->
                    AvailableCard(extension = av, onInstall = { model.install(av) }, busy = busy)
                }
                item {
                    Text(
                        "Load errors",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                items(errors.toList(), key = { it.first }) { (file, reason) ->
                    Text(
                        "$file: $reason",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

class ExtensionsScreenModel : ScreenModel, KoinComponent {

    private val manager: ExtensionManager by inject()

    val installed: StateFlow<List<InstalledExtension>> = manager.installed
    val available: StateFlow<List<AvailableExtension>> = manager.available
    val errors: StateFlow<Map<String, String>> = manager.loadErrors

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    init {
        screenModelScope.launch {
            manager.reload()
            manager.refreshIndex()
        }
    }

    fun refresh() {
        screenModelScope.launch {
            _busy.value = true
            runCatching {
                manager.refreshIndex()
                manager.reload()
            }
            _busy.value = false
        }
    }

    fun install(extension: AvailableExtension) {
        screenModelScope.launch {
            _busy.value = true
            runCatching { manager.install(extension) }
            _busy.value = false
        }
    }

    fun update(pkg: String) {
        screenModelScope.launch {
            _busy.value = true
            runCatching { manager.updateAvailable(pkg) }
            _busy.value = false
        }
    }

    fun setEnabled(pkg: String, enabled: Boolean) {
        screenModelScope.launch { manager.setEnabled(pkg, enabled) }
    }

    fun remove(pkg: String) {
        screenModelScope.launch { manager.remove(pkg) }
    }
}

@Composable
private fun InstalledCard(
    extension: InstalledExtension,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
    updateAvailable: AvailableExtension?,
    onUpdate: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(extension.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "v${extension.versionName} \u2022 ${extension.provider}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (updateAvailable != null) {
                TextButton(onClick = onUpdate) { Text("Update") }
            }
            Switch(checked = extension.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove")
            }
        }
    }
}

@Composable
private fun AvailableCard(
    extension: AvailableExtension,
    onInstall: () -> Unit,
    busy: Boolean,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(extension.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "v${extension.versionName} \u2022 ${extension.provider}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onInstall, enabled = !busy) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Text("Install")
            }
        }
    }
}
