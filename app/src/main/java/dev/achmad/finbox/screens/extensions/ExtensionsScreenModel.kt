package dev.achmad.finbox.screens.extensions

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.model.InstalledExtension
import dev.achmad.finbox.core.di.inject
import dev.achmad.finbox.extension.AvailableExtension
import dev.achmad.finbox.extension.ExtensionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ExtensionsScreenModel(
    private val manager: ExtensionManager = inject(),
) : ScreenModel {

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
