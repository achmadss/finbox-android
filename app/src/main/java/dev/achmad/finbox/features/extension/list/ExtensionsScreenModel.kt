package dev.achmad.finbox.features.extension.list

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

/**
 * The extensions this build ships, and whether each is switched on.
 *
 * There is nothing to load, fetch or reconcile: the list is a compile-time
 * constant and the only thing that moves is the switch. What stood here drove
 * a repo index, install progress per package, an update count and a trust
 * prompt.
 */
class ExtensionsScreenModel(
    private val manager: ExtensionManager = inject(),
    preferences: ExtensionPreferences = inject(),
) : StateScreenModel<ExtensionsScreenModel.State>(State(rows = rowsOf(manager, emptySet()))) {

    init {
        screenModelScope.launch {
            preferences.disabledExtensions().changes()
                .map { disabled -> rowsOf(manager, disabled) }
                .collect { rows -> mutableState.update { it.copy(rows = rows) } }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) = manager.setEnabled(id, enabled)

    @Immutable
    data class State(val rows: List<ExtensionRow>)

    private companion object {
        fun rowsOf(manager: ExtensionManager, disabled: Set<String>): List<ExtensionRow> =
            manager.all.map { ExtensionRow(it, enabled = it.id !in disabled) }
    }
}

@Immutable
data class ExtensionRow(
    val extension: Extension,
    val enabled: Boolean,
) {
    val id: String get() = extension.id
    val name: String get() = extension.name
}
