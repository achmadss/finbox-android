package dev.achmad.finbox.features.settings

import android.content.Context
import android.net.Uri
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.backup.BACKUP_EXTENSION
import dev.achmad.data.backup.FinboxBackup
import dev.achmad.data.export.CsvExport
import dev.achmad.data.repository.AccountRepository
import dev.achmad.finbox.core.statement.StatementUpdateJob
import dev.achmad.finbox.core.update.AppUpdateChecker
import dev.achmad.finbox.util.koin.inject
import dev.achmad.finbox.util.koin.injectAndroidContext
import dev.achmad.finbox.util.ui.ToastHelper
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsScreenModel : ScreenModel {

    private val context: Context = injectAndroidContext()
    private val backup = inject<FinboxBackup>()
    private val csv = inject<CsvExport>()
    private val accountRepository = inject<AccountRepository>()
    private val appUpdateChecker = inject<AppUpdateChecker>()
    private val toast = inject<ToastHelper>()

    /** The newest fetch across accounts — what "last fetched" means to the user. */
    val lastSync: StateFlow<Long?> = accountRepository.accounts()
        .map { accounts -> accounts.mapNotNull { it.lastSyncAt }.maxOrNull() }
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Held while a file is being written or read, so a second tap can't join in. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun backupFileName(): String = "finbox_${LocalDate.now()}.$BACKUP_EXTENSION"

    fun csvFileName(): String = "finbox_${LocalDate.now()}.csv"

    fun createBackup(uri: Uri) = runExclusive("Backup saved") {
        context.contentResolver.openOutputStream(uri)?.use { backup.backupTo(it) }
            ?: error("Could not open the file")
    }

    fun restoreBackup(uri: Uri) = runExclusive("Backup restored") {
        context.contentResolver.openInputStream(uri)?.use { backup.restoreFrom(it) }
            ?: error("Could not open the file")
    }

    fun exportCsv(uri: Uri) = runExclusive("Transactions exported") {
        context.contentResolver.openOutputStream(uri)?.use { csv.exportTo(it) }
            ?: error("Could not open the file")
    }

    fun fetchNow(context: Context) {
        screenModelScope.launch { StatementUpdateJob.runNow(context) }
    }

    /** Hands stored mail to the current extensions again, in the background. */
    fun reindexTransactions(context: Context) {
        screenModelScope.launch { StatementUpdateJob.reparseNow(context) }
    }

    /**
     * Forced, so neither the daily throttle nor the switch swallows it.
     *
     * Extensions have no equivalent here: their list refreshes itself whenever
     * the extensions screen opens, which is where a new one gets installed.
     */
    fun checkAppUpdateNow() {
        screenModelScope.launch {
            runCatching { appUpdateChecker.checkForUpdate(force = true) }
                .onSuccess { update ->
                    toast.show(
                        update?.let { "Finbox ${it.version} is available" }
                            ?: "Finbox is up to date",
                    )
                }
                .onFailure { toast.show("Could not check for app updates") }
        }
    }

    private fun runExclusive(success: String, block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        screenModelScope.launch {
            runCatching { block() }
                .onSuccess { toast.show(success) }
                .onFailure { toast.show(it.message ?: "Something went wrong") }
            _busy.value = false
        }
    }
}
