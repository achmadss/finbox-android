package dev.achmad.finbox.features.extension.detail

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.repository.TransactionRepository
import dev.achmad.finbox.R
import dev.achmad.finbox.core.preference.ExtensionMethodPreference
import dev.achmad.finbox.core.extension.ExtensionManager
import dev.achmad.finbox.core.extension.InstallStep
import dev.achmad.finbox.core.update.transaction.TransactionUpdateManager
import dev.achmad.finbox.features.extension.list.ExtensionUiModel
import dev.achmad.finbox.extension.TransactionDirection
import dev.achmad.finbox.util.koin.inject
import java.io.File
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.collections.orEmpty

class ExtensionDetailScreenModel(
    private val pkg: String,
    private val manager: ExtensionManager = inject(),
    private val methodPreference: ExtensionMethodPreference = inject(),
    private val transactionRepository: TransactionRepository = inject(),
    private val transactionUpdateManager: TransactionUpdateManager = inject(),
) : StateScreenModel<ExtensionDetailScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            combine(
                manager.installed,
                manager.available,
                manager.extensionsFlow,
                methodPreference.disabled(pkg).changes(),
                manager.installSteps,
            ) { installed, available, _, disabled, steps ->
                installed.firstOrNull { it.pkg == pkg }?.let { extension ->
                    ExtensionUiModel.Installed(
                        extension = extension,
                        update = available.firstOrNull {
                            it.pkg == pkg && it.versionCode > extension.versionCode
                        },
                        // The manager runs the install, so its progress is the
                        // truth here — including one started from the other screen.
                        installStep = steps[pkg] ?: InstallStep.Idle,
                    ) to methods(extension.extensionIds, disabled)
                }
            }.collect { item ->
                mutableState.update {
                    it.copy(
                        extension = item?.first,
                        methods = item?.second.orEmpty(),
                        sizeBytes = item?.first?.extension?.file
                            ?.let { path -> File(path).length().takeIf { size -> size > 0 } },
                    )
                }
            }
        }
    }

    /**
     * The methods this extension declares, in its own order, each with the user's
     * switch. Empty until the registry has loaded — the methods live in the APK.
     */
    private fun methods(extensionIds: List<Long>, disabled: Set<String>): List<MethodUiModel> =
        extensionIds
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
     * Off drops what the method already parsed; on re-reads the mail this extension
     * claimed. Neither touches Gmail — the bodies are stored.
     */
    fun toggleMethod(key: String) {
        screenModelScope.launch {
            val extensionIds = state.value.extension?.extension?.extensionIds.orEmpty().toSet()
            if (methodPreference.toggle(pkg, key)) {
                transactionUpdateManager.reparseExtensionsNow(extensionIds)
            } else {
                transactionRepository.deleteByMethod(extensionIds, key)
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
        val extension: ExtensionUiModel.Installed? = null,
        val methods: List<MethodUiModel> = emptyList(),
        /** The APK on disk. Null while the row has no file recorded yet. */
        val sizeBytes: Long? = null,
        val uninstalled: Boolean = false,
    ) {
        /**
         * Unknown rather than assumed while [methods] is empty: the registry loads
         * after the database does, and "Expense only" flickering into "Multi
         * method" reads as a bug.
         */
        @get:StringRes
        val summary: Int
            get() = when {
                methods.isEmpty() -> R.string.unknown
                methods.all { it.direction == TransactionDirection.OUTGOING } -> R.string.extension_summary_expense_only
                methods.all { it.direction == TransactionDirection.INCOMING } -> R.string.extension_summary_income_only
                else -> R.string.extension_summary_multi_method
            }
    }
}
