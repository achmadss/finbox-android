package dev.achmad.finbox.features.parser.detail

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.repository.TransactionRepository
import dev.achmad.finbox.R
import dev.achmad.finbox.core.preference.ParserMethodPreference
import dev.achmad.finbox.core.parser.ParserManager
import dev.achmad.finbox.core.parser.InstallStep
import dev.achmad.finbox.core.update.transaction.TransactionUpdateManager
import dev.achmad.finbox.features.parser.list.ParserUiModel
import dev.achmad.finbox.parser.TransactionDirection
import dev.achmad.finbox.util.koin.inject
import java.io.File
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.collections.orEmpty

class ParserDetailScreenModel(
    private val pkg: String,
    private val manager: ParserManager = inject(),
    private val methodPreference: ParserMethodPreference = inject(),
    private val transactionRepository: TransactionRepository = inject(),
    private val transactionUpdateManager: TransactionUpdateManager = inject(),
) : StateScreenModel<ParserDetailScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            combine(
                manager.installed,
                manager.available,
                manager.parsersFlow,
                methodPreference.disabled(pkg).changes(),
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
                    ) to methods(parser.parserIds, disabled)
                }
            }.collect { item ->
                mutableState.update {
                    it.copy(
                        parser = item?.first,
                        methods = item?.second.orEmpty(),
                        sizeBytes = item?.first?.parser?.file
                            ?.let { path -> File(path).length().takeIf { size -> size > 0 } },
                    )
                }
            }
        }
    }

    /**
     * The methods this parser declares, in its own order, each with the user's
     * switch.
     *
     * Empty until the registry has loaded — the methods live in the APK, not the
     * database, so nothing else knows them.
     */
    private fun methods(parserIds: List<Long>, disabled: Set<String>): List<MethodUiModel> =
        parserIds
            .mapNotNull { manager.getById(it) }
            .flatMap { it.methods() }
            .distinctBy { it.key }
            .map { MethodUiModel(it.key, it.name, it.direction, enabled = it.key !in disabled) }

    fun setEnabled(enabled: Boolean) {
        screenModelScope.launch {
            manager.setEnabled(pkg, enabled)
            transactionUpdateManager.reparseNow()
        }
    }

    /**
     * Off drops what the method already parsed; on re-reads the mail this parser
     * claimed, which is where those transactions come back from. Neither touches
     * Gmail — the bodies are stored.
     */
    fun toggleMethod(key: String) {
        screenModelScope.launch {
            val parserIds = state.value.parser?.parser?.parserIds.orEmpty().toSet()
            if (methodPreference.toggle(pkg, key)) {
                transactionUpdateManager.reparseParsersNow(parserIds)
            } else {
                transactionRepository.deleteByMethod(parserIds, key)
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
    data class MethodUiModel(
        val key: String,
        val name: String,
        val direction: TransactionDirection,
        val enabled: Boolean,
    )

    @Immutable
    data class State(
        val parser: ParserUiModel.Installed? = null,
        val methods: List<MethodUiModel> = emptyList(),
        /** The APK on disk. Null while the row has no file recorded yet. */
        val sizeBytes: Long? = null,
        val uninstalled: Boolean = false,
    ) {
        /**
         * What the parser deals in, for the line under the version.
         *
         * Unknown rather than assumed while [methods] is empty: the registry loads
         * after the database does, and "Expense only" flickering into "Multi
         * method" reads as a bug.
         */
        @get:StringRes
        val summary: Int
            get() = when {
                methods.isEmpty() -> R.string.unknown
                methods.all { it.direction == TransactionDirection.OUTGOING } -> R.string.parser_summary_expense_only
                methods.all { it.direction == TransactionDirection.INCOMING } -> R.string.parser_summary_income_only
                else -> R.string.parser_summary_multi_method
            }
    }
}
