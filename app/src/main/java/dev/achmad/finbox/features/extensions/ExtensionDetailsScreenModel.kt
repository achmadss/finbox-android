package dev.achmad.finbox.features.extensions

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.finbox.core.extension.ExtensionManager
import dev.achmad.finbox.core.extension.InstallStep
import dev.achmad.finbox.core.statement.StatementUpdateJob
import dev.achmad.finbox.util.koin.inject
import dev.achmad.finbox.util.koin.injectAndroidContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExtensionDetailsScreenModel(
    private val pkg: String,
    private val manager: ExtensionManager = inject(),
) : StateScreenModel<ExtensionDetailsScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            combine(manager.installed, manager.available) { installed, available ->
                installed.firstOrNull { it.pkg == pkg }?.let { extension ->
                    ExtensionUiModel.Installed(
                        extension = extension,
                        update = available.firstOrNull {
                            it.pkg == pkg && it.versionCode > extension.versionCode
                        },
                    )
                }
            }.collect { item ->
                mutableState.update { it.copy(extension = item?.copy(installStep = it.installStep)) }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        screenModelScope.launch {
            manager.setEnabled(pkg, enabled)
            reparse()
        }
    }

    fun update() {
        screenModelScope.launch {
            var last = InstallStep.Idle
            manager.updateExtension(pkg)
                .onEach { step ->
                    last = step
                    mutableState.update { it.copy(installStep = step) }
                }
                .collect()
            if (last == InstallStep.Installed) reparse()
        }
    }

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

    private suspend fun reparse() {
        StatementUpdateJob.reparseNow(injectAndroidContext())
    }

    @Immutable
    data class State(
        val extension: ExtensionUiModel.Installed? = null,
        val installStep: InstallStep = InstallStep.Idle,
        val uninstalled: Boolean = false,
    )
}
