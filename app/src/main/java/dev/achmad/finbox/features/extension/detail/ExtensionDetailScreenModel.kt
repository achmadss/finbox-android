package dev.achmad.finbox.features.extension.detail

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.finbox.core.extension.ExtensionManager
import dev.achmad.finbox.core.extension.InstallStep
import dev.achmad.finbox.core.update.transaction.TransactionUpdateManager
import dev.achmad.finbox.features.extension.list.ExtensionUiModel
import dev.achmad.finbox.util.koin.inject
import java.io.File
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExtensionDetailScreenModel(
    private val pkg: String,
    private val manager: ExtensionManager = inject(),
    private val transactionUpdateManager: TransactionUpdateManager = inject(),
) : StateScreenModel<ExtensionDetailScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            combine(
                manager.installed,
                manager.available,
                manager.extensionsFlow,
                manager.installSteps,
            ) { installed, available, _, steps ->
                installed.firstOrNull { it.pkg == pkg }?.let { extension ->
                    ExtensionUiModel.Installed(
                        extension = extension,
                        update = available.firstOrNull {
                            it.pkg == pkg && it.versionCode > extension.versionCode
                        },
                        // The manager runs the install, so its progress is the
                        // truth here — including one started from the other screen.
                        installStep = steps[pkg] ?: InstallStep.Idle,
                    )
                }
            }.collect { item ->
                mutableState.update {
                    it.copy(
                        extension = item,
                        sizeBytes = item?.extension?.file
                            ?.let { path -> File(path).length().takeIf { size -> size > 0 } },
                    )
                }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        screenModelScope.launch {
            manager.setEnabled(pkg, enabled)
            transactionUpdateManager.reparseNow()
        }
    }

    /** Runs in the manager, so leaving this screen does not cancel the download. */
    fun update() = manager.update(pkg)

    /**
     * Leaving is the screen's job, not this one's: popping first would cancel
     * [screenModelScope] with the APK half removed.
     */
    fun uninstall() {
        screenModelScope.launch {
            manager.remove(pkg)
            mutableState.update { it.copy(uninstalled = true) }
        }
    }

    @Immutable
    data class State(
        val extension: ExtensionUiModel.Installed? = null,
        /** The APK on disk. Null while the row has no file recorded yet. */
        val sizeBytes: Long? = null,
        val uninstalled: Boolean = false,
    )
}
