package dev.achmad.finbox.features.source.detail

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.finbox.core.source.SourceManager
import dev.achmad.finbox.core.preference.SourcePreferences
import dev.achmad.finbox.source.core.SourceEntry
import dev.achmad.finbox.util.koin.inject
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SourceDetailScreenModel(
    private val id: String,
    private val manager: SourceManager = inject(),
    preferences: SourcePreferences = inject(),
) : StateScreenModel<SourceDetailScreenModel.State>(
    State(source = manager.all.firstOrNull { it.id == id }, enabled = manager.isEnabled(id)),
) {

    init {
        screenModelScope.launch {
            preferences.disabledSources().changes()
                .map { id !in it }
                .collect { enabled -> mutableState.update { it.copy(enabled = enabled) } }
        }
    }

    fun setEnabled(enabled: Boolean) = manager.setEnabled(id, enabled)

    /**
     * A null [source] is an id nothing answers to.
     *
     * Reachable only from a stale navigation entry now that the list is a
     * constant, but a screen that renders nothing rather than crashing is worth
     * the one null check.
     */
    @Immutable
    data class State(
        val source: SourceEntry?,
        val enabled: Boolean,
    )
}
