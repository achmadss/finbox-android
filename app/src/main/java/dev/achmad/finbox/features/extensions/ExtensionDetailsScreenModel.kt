package dev.achmad.finbox.features.extensions

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.repository.TransactionRepository
import dev.achmad.finbox.core.extension.ExtensionKindPreference
import dev.achmad.finbox.core.extension.ExtensionManager
import dev.achmad.finbox.core.extension.InstallStep
import dev.achmad.finbox.core.statement.StatementUpdateJob
import dev.achmad.finbox.extension.TransactionType
import dev.achmad.finbox.util.koin.inject
import dev.achmad.finbox.util.koin.injectAndroidContext
import java.io.File
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExtensionDetailsScreenModel(
    private val pkg: String,
    private val manager: ExtensionManager = inject(),
    private val kindPreference: ExtensionKindPreference = inject(),
    private val transactionRepository: TransactionRepository = inject(),
) : StateScreenModel<ExtensionDetailsScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            combine(
                manager.installed,
                manager.available,
                manager.sourcesFlow,
                kindPreference.disabled(pkg).changes(),
            ) { installed, available, _, disabled ->
                installed.firstOrNull { it.pkg == pkg }?.let { extension ->
                    ExtensionUiModel.Installed(
                        extension = extension,
                        update = available.firstOrNull {
                            it.pkg == pkg && it.versionCode > extension.versionCode
                        },
                    ) to kinds(extension.sourceIds, disabled)
                }
            }.collect { item ->
                mutableState.update {
                    it.copy(
                        extension = item?.first?.copy(installStep = it.installStep),
                        kinds = item?.second.orEmpty(),
                        sizeBytes = item?.first?.extension?.file
                            ?.let { path -> File(path).length().takeIf { size -> size > 0 } },
                    )
                }
            }
        }
    }

    /**
     * The transaction kinds this extension's sources declare, in the order they
     * declared them, each with the user's switch.
     *
     * Empty until the source registry has loaded — the kinds live in the APK,
     * not the database, so nothing else knows them.
     */
    private fun kinds(sourceIds: List<Long>, disabled: Set<String>): List<KindUiModel> =
        sourceIds
            .mapNotNull { manager.getById(it) }
            .flatMap { it.kinds }
            .distinctBy { it.key }
            .map { KindUiModel(it.key, it.name, it.type, enabled = it.key !in disabled) }

    fun setEnabled(enabled: Boolean) {
        screenModelScope.launch {
            manager.setEnabled(pkg, enabled)
            reparse()
        }
    }

    /**
     * Switching a kind off drops what it already parsed; switching it on re-reads
     * the mail those sources claimed, which is where those transactions come back
     * from. Neither touches Gmail — the bodies are stored.
     */
    fun toggleKind(key: String) {
        screenModelScope.launch {
            val sourceIds = state.value.extension?.extension?.sourceIds.orEmpty().toSet()
            if (kindPreference.toggle(pkg, key)) {
                StatementUpdateJob.reparseSourcesNow(injectAndroidContext(), sourceIds)
            } else {
                transactionRepository.deleteByKind(sourceIds, key)
            }
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
    data class KindUiModel(
        val key: String,
        val name: String,
        val type: TransactionType,
        val enabled: Boolean,
    )

    @Immutable
    data class State(
        val extension: ExtensionUiModel.Installed? = null,
        val kinds: List<KindUiModel> = emptyList(),
        /** The APK on disk. Null while the row has no file recorded yet. */
        val sizeBytes: Long? = null,
        val installStep: InstallStep = InstallStep.Idle,
        val uninstalled: Boolean = false,
    ) {
        /**
         * What the extension deals in, for the line under the version.
         *
         * Unknown rather than assumed while [kinds] is empty: the registry loads
         * after the database does, and "Expense only" flickering into "Multi
         * type" reads as a bug.
         */
        val summary: String
            get() = when {
                kinds.isEmpty() -> "Unknown"
                kinds.all { it.type == TransactionType.EXPENSE } -> "Expense only"
                kinds.all { it.type == TransactionType.INCOME } -> "Income only"
                else -> "Multi type"
            }
    }
}
