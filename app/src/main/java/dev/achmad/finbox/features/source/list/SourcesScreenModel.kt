package dev.achmad.finbox.features.source.list

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

/**
 * The sources this build ships, and whether each is switched on.
 *
 * There is nothing to load, fetch or reconcile: the list is a compile-time
 * constant and the only thing that moves is the switch. What stood here drove
 * a repo index, install progress per package, an update count and a trust
 * prompt.
 */
class SourcesScreenModel(
    private val manager: SourceManager = inject(),
    preferences: SourcePreferences = inject(),
) : StateScreenModel<SourcesScreenModel.State>(State(rows = rowsOf(manager, emptySet()))) {

    init {
        screenModelScope.launch {
            preferences.disabledSources().changes()
                .map { disabled -> rowsOf(manager, disabled) }
                .collect { rows -> mutableState.update { it.copy(rows = rows) } }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) = manager.setEnabled(id, enabled)

    @Immutable
    data class State(val rows: List<SourceRow>)

    private companion object {
        fun rowsOf(manager: SourceManager, disabled: Set<String>): List<SourceRow> =
            manager.all.map { SourceRow(it, enabled = it.id !in disabled) }
    }
}

@Immutable
data class SourceRow(
    val source: SourceEntry,
    val enabled: Boolean,
) {
    val id: String get() = source.id
    val name: String get() = source.name
}
