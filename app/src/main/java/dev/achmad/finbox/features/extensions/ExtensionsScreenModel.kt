package dev.achmad.finbox.features.extensions

import android.util.Log
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.model.InstalledExtension
import dev.achmad.finbox.core.extension.AvailableExtension
import dev.achmad.finbox.core.extension.ExtensionManager
import dev.achmad.finbox.core.extension.ExtensionUpdateNotifier
import dev.achmad.finbox.core.extension.InstallStep
import dev.achmad.finbox.core.statement.StatementUpdateJob
import dev.achmad.finbox.util.koin.inject
import dev.achmad.finbox.util.koin.injectAndroidContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExtensionsScreenModel(
    private val manager: ExtensionManager = inject(),
) : StateScreenModel<ExtensionsScreenModel.State>(State()) {

    /** pkg -> where its install or update has got to. */
    private val currentDownloads = MutableStateFlow<Map<String, InstallStep>>(emptyMap())

    /** Kept so the row's cancel button has something to stop. */
    private val jobs = mutableMapOf<String, Job>()

    init {
        screenModelScope.launch {
            combine(
                manager.installed,
                manager.available,
                manager.loadErrors,
                currentDownloads,
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
                if (count == 0) ExtensionUpdateNotifier(injectAndroidContext()).dismiss()
            }
        }
    }

    fun refresh() {
        screenModelScope.launch {
            mutableState.update { it.copy(isRefreshing = true) }
            runCatching {
                manager.refreshIndex()
                manager.reload()
            }.onFailure { Log.e("Extensions", "Extension refresh failed", it) }
            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    fun install(extension: AvailableExtension) = track(extension.pkg) {
        manager.installExtension(extension)
    }

    fun update(pkg: String) = track(pkg) { manager.updateExtension(pkg) }

    fun updateAll() = state.value.updates.forEach { update(it.pkg) }

    /** Ends the download; nothing was written yet, so there is nothing to undo. */
    fun cancelInstall(pkg: String) {
        jobs.remove(pkg)?.cancel()
        currentDownloads.update { it - pkg }
    }

    fun setEnabled(pkg: String, enabled: Boolean) {
        screenModelScope.launch {
            manager.setEnabled(pkg, enabled)
            reparse()
        }
    }

    fun uninstall(pkg: String) {
        screenModelScope.launch { manager.remove(pkg) }
    }

    private fun track(pkg: String, steps: () -> Flow<InstallStep>) {
        jobs[pkg]?.cancel()
        jobs[pkg] = screenModelScope.launch {
            var last = InstallStep.Idle
            steps()
                .onEach { step ->
                    last = step
                    currentDownloads.update { it + (pkg to step) }
                }
                .onCompletion {
                    jobs.remove(pkg)
                    // A finished install redraws from the installed list, but a failure
                    // has to stay on the row or it goes back to looking untouched.
                    if (last != InstallStep.Error) currentDownloads.update { it - pkg }
                }
                .collect()
            if (last == InstallStep.Installed) reparse()
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
 * The three lists the screen draws, from the two the manager keeps.
 *
 * An installed extension with a newer entry in the index moves out of
 * "installed" and into "updates"; the index entry for something already
 * installed is not offered again.
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
    val provider: String
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
        override val provider get() = extension.provider
        override val versionName get() = extension.versionName
        val enabled get() = extension.enabled
    }

    data class Available(
        val extension: AvailableExtension,
        override val installStep: InstallStep = InstallStep.Idle,
    ) : ExtensionUiModel {
        override val pkg get() = extension.pkg
        override val name get() = extension.name
        override val provider get() = extension.provider
        override val versionName get() = extension.versionName
    }
}
