package dev.achmad.finbox.features.parser.detail

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.repository.TransactionRepository
import dev.achmad.finbox.core.preference.ParserKindPreference
import dev.achmad.finbox.core.parser.ParserManager
import dev.achmad.finbox.core.parser.InstallStep
import dev.achmad.finbox.core.update.transaction.TransactionUpdateJob
import dev.achmad.finbox.features.parser.list.ParserUiModel
import dev.achmad.finbox.parser.TransactionType
import dev.achmad.finbox.util.koin.inject
import dev.achmad.finbox.util.koin.injectAndroidContext
import java.io.File
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.collections.orEmpty

class ParserDetailScreenModel(
    private val pkg: String,
    private val manager: ParserManager = inject(),
    private val kindPreference: ParserKindPreference = inject(),
    private val transactionRepository: TransactionRepository = inject(),
) : StateScreenModel<ParserDetailScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            combine(
                manager.installed,
                manager.available,
                manager.sourcesFlow,
                kindPreference.disabled(pkg).changes(),
                manager.installSteps,
            ) { installed, available, _, disabled, steps ->
                installed.firstOrNull { it.pkg == pkg }?.let { parser ->
                    ParserUiModel.Installed(
                        parser = parser,
                        update = available.firstOrNull {
                            it.pkg == pkg && it.versionCode > parser.versionCode
                        },
                        // The manager runs the install, so its progress is the
                        // truth here — including one started from the other screen.
                        installStep = steps[pkg] ?: InstallStep.Idle,
                    ) to kinds(parser.sourceIds, disabled)
                }
            }.collect { item ->
                mutableState.update {
                    it.copy(
                        parser = item?.first,
                        kinds = item?.second.orEmpty(),
                        sizeBytes = item?.first?.parser?.file
                            ?.let { path -> File(path).length().takeIf { size -> size > 0 } },
                    )
                }
            }
        }
    }

    /**
     * The transaction kinds this parser's sources declare, in the order they
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
            TransactionUpdateJob.reparseNow(injectAndroidContext())
        }
    }

    /**
     * Switching a kind off drops what it already parsed; switching it on re-reads
     * the mail those sources claimed, which is where those transactions come back
     * from. Neither touches Gmail — the bodies are stored.
     */
    fun toggleKind(key: String) {
        screenModelScope.launch {
            val sourceIds = state.value.parser?.parser?.sourceIds.orEmpty().toSet()
            if (kindPreference.toggle(pkg, key)) {
                TransactionUpdateJob.reparseSourcesNow(injectAndroidContext(), sourceIds)
            } else {
                transactionRepository.deleteByKind(sourceIds, key)
            }
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
    data class KindUiModel(
        val key: String,
        val name: String,
        val type: TransactionType,
        val enabled: Boolean,
    )

    @Immutable
    data class State(
        val parser: ParserUiModel.Installed? = null,
        val kinds: List<KindUiModel> = emptyList(),
        /** The APK on disk. Null while the row has no file recorded yet. */
        val sizeBytes: Long? = null,
        val uninstalled: Boolean = false,
    ) {
        /**
         * What the parser deals in, for the line under the version.
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
