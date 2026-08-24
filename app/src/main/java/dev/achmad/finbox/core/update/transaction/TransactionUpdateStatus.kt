package dev.achmad.finbox.core.update.transaction

import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Observes active transaction-update work for app-level status UI. */
class TransactionUpdateStatus(
    private val workManager: WorkManager,
) {

    val imported: Flow<Int?> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(TransactionUpdateWork.WORK_NAME),
        workManager.getWorkInfosForUniqueWorkFlow(TransactionUpdateWork.MANUAL_WORK_NAME),
        workManager.getWorkInfosForUniqueWorkFlow(TransactionUpdateWork.REPARSE_WORK_NAME),
    ) { periodic, manual, reparse ->
        val running = (periodic + manual + reparse).filter { it.state == WorkInfo.State.RUNNING }
        if (running.isEmpty()) {
            null
        } else {
            running.sumOf { it.progress.getInt(TransactionUpdateWork.PROGRESS_IMPORTED, 0) }
        }
    }.distinctUntilChanged()
}
