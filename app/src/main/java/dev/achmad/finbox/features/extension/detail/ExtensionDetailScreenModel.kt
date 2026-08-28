package dev.achmad.finbox.features.extension.detail

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.finbox.core.extension.ExtensionManager
import dev.achmad.finbox.core.preference.ExtensionPreferences
import dev.achmad.finbox.extension.Extension
import dev.achmad.finbox.util.koin.inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExtensionDetailScreenModel(
    private val id: String,
    private val manager: ExtensionManager = inject(),
    preferences: ExtensionPreferences = inject(),
) : StateScreenModel<ExtensionDetailScreenModel.State>(
    State(extension = manager.all.firstOrNull { it.id == id }, enabled = manager.isEnabled(id)),
) {

    init {
        screenModelScope.launch {
            preferences.disabledExtensions().changes()
                .map { id !in it }
                .collect { enabled -> mutableState.update { it.copy(enabled = enabled) } }
        }
    }

    fun setEnabled(enabled: Boolean) = manager.setEnabled(id, enabled)

    /**
     * A null [extension] is an id nothing answers to.
     *
     * Reachable only from a stale navigation entry now that the list is a
     * constant, but a screen that renders nothing rather than crashing is worth
     * the one null check.
     */
    @Immutable
    data class State(
        val extension: Extension?,
        val enabled: Boolean,
    )
}
