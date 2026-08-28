package dev.achmad.finbox.features.extension.list

import android.util.Log
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.model.InstalledExtension
import dev.achmad.finbox.core.extension.AvailableExtension
import dev.achmad.finbox.core.extension.ExtensionManager
import dev.achmad.finbox.core.extension.ExtensionUpdateNotifier
import dev.achmad.finbox.core.extension.InstallStep
import dev.achmad.finbox.core.update.transaction.TransactionUpdateManager
import dev.achmad.finbox.util.koin.inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class ExtensionsScreenModel(
    private val manager: ExtensionManager = inject(),
    private val extensionUpdateNotifier: ExtensionUpdateNotifier = inject(),
    private val transactionUpdateManager: TransactionUpdateManager = inject(),
) : StateScreenModel<ExtensionsScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            combine(
                manager.installed,
                manager.available,
                manager.loadErrors,
                manager.installSteps,
            ) { installed, available, errors, downloads ->
                groupExtensions(installed, available, errors, downloads)
            }.collect { fresh ->
                mutableState.update { fresh.copy(isRefreshing = it.isRefreshing) }
            }
        }

        screenModelScope.launch {
            // Already loaded if an update ran in this process; cheap and idempotent
            // if the screen is what opened first.
            manager.reload()
            runCatching { manager.refreshIndex() }
                .onFailure { Log.e("Extensions", "Extension index fetch failed", it) }
        }

        // Nothing left to update means the notification is stale, whoever cleared it.
        screenModelScope.launch {
            manager.updatesCount.collect { count ->
                if (count == 0) extensionUpdateNotifier.dismiss()
            }
        }
    }

    fun refresh() {
        screenModelScope.launch {
            mutableState.update { it.copy(isRefreshing = true) }
            // The index is one small fetch, so the indicator would otherwise blink in
            // and straight back out with nothing read from it.
            delay(1.seconds)
            runCatching {
                manager.refreshIndex()
                manager.reload()
            }.onFailure { Log.e("Extensions", "Extension refresh failed", it) }
            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    // Installs run in the manager, not here: this screen model dies when the user
    // navigates away, and a download must not die with it.
    fun install(extension: AvailableExtension) = manager.install(extension)

    fun update(pkg: String) = manager.update(pkg)

    fun updateAll() = state.value.updates.forEach { update(it.pkg) }

    fun cancelInstall(pkg: String) = manager.cancelInstall(pkg)

    fun setEnabled(pkg: String, enabled: Boolean) {
        screenModelScope.launch {
            manager.setEnabled(pkg, enabled)
            // An extension switched on reads the mail it has not tried yet. Handed to a
            // job, which outlives this screen.
            transactionUpdateManager.reparseNow()
        }
    }

    fun uninstall(pkg: String) {
        screenModelScope.launch { manager.remove(pkg) }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val updates: List<ExtensionUiModel.Installed> = emptyList(),
        val installed: List<ExtensionUiModel.Installed> = emptyList(),
        val available: List<ExtensionUiModel.Available> = emptyList(),
        val errors: Map<String, String> = emptyMap(),
    ) {
        val isEmpty = updates.isEmpty() && installed.isEmpty() && available.isEmpty() && errors.isEmpty()
    }
}

/**
 * The three lists the screen draws, from the two the manager keeps. An installed
 * extension with a newer index entry moves out of "installed" into "updates"; the
 * index entry for something already installed is not offered again.
 */
fun groupExtensions(
    installed: List<InstalledExtension>,
    available: List<AvailableExtension>,
    errors: Map<String, String>,
    downloads: Map<String, InstallStep>,
): ExtensionsScreenModel.State {
    val installedItems = installed
        .map { extension ->
            ExtensionUiModel.Installed(
                extension = extension,
                update = available.firstOrNull {
                    it.pkg == extension.pkg && it.versionCode > extension.versionCode
                },
                installStep = downloads[extension.pkg] ?: InstallStep.Idle,
            )
        }
        .sortedBy { it.name.lowercase() }
    return ExtensionsScreenModel.State(
        isLoading = false,
        updates = installedItems.filter { it.update != null },
        installed = installedItems.filter { it.update == null },
        available = available
            .filter { entry -> installed.none { it.pkg == entry.pkg } }
            .map {
                ExtensionUiModel.Available(
                    extension = it,
                    installStep = downloads[it.pkg] ?: InstallStep.Idle,
                )
            }
            .sortedBy { it.name.lowercase() },
        errors = errors,
    )
}

sealed interface ExtensionUiModel {
    val pkg: String
    val name: String
    val versionName: String
    val installStep: InstallStep

    val isRunning: Boolean get() = !installStep.isCompleted()

    data class Installed(
        val extension: InstalledExtension,
        val update: AvailableExtension?,
        override val installStep: InstallStep = InstallStep.Idle,
    ) : ExtensionUiModel {
        override val pkg get() = extension.pkg
        override val name get() = extension.name
        override val versionName get() = extension.versionName
        val enabled get() = extension.enabled
    }

    data class Available(
        val extension: AvailableExtension,
        override val installStep: InstallStep = InstallStep.Idle,
    ) : ExtensionUiModel {
        override val pkg get() = extension.pkg
        override val name get() = extension.name
        override val versionName get() = extension.versionName
    }
}
