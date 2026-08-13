package dev.achmad.finbox.features.extensions

import android.util.Log
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.model.InstalledExtension
import dev.achmad.finbox.core.util.koin.inject
import dev.achmad.finbox.core.util.koin.injectAndroidContext
import dev.achmad.finbox.core.extension.AvailableExtension
import dev.achmad.finbox.core.extension.ExtensionManager
import dev.achmad.finbox.core.statement.StatementUpdateJob
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
            // Already loaded if an update ran in this process; cheap and idempotent
            // if the screen is what opened first.
            manager.reload()
            runCatching { manager.refreshIndex() }
                .onFailure { Log.e("Extensions", "Extension index fetch failed", it) }
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
            reparse()
            _busy.value = false
        }
    }

    fun update(pkg: String) {
        screenModelScope.launch {
            _busy.value = true
            runCatching { manager.updateAvailable(pkg) }
            reparse()
            _busy.value = false
        }
    }

    fun setEnabled(pkg: String, enabled: Boolean) {
        screenModelScope.launch {
            manager.setEnabled(pkg, enabled)
            reparse()
        }
    }

    /**
     * A parser that wasn't there before reads the mail it hasn't tried yet.
     *
     * Handed to a job: it downloads a body per untried email, which takes longer
     * than this screen is guaranteed to live.
     */
    private suspend fun reparse() {
        StatementUpdateJob.reparseNow(injectAndroidContext())
    }

    fun remove(pkg: String) {
        screenModelScope.launch { manager.remove(pkg) }
    }
}
