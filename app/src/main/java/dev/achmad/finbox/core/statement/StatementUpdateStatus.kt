package dev.achmad.finbox.core.statement

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Observes active statement work for app-level status UI. */
class StatementUpdateStatus(context: Context) {

    private val workManager = WorkManager.getInstance(context.applicationContext)

    val imported: Flow<Int?> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(StatementUpdateJob.WORK_NAME),
        workManager.getWorkInfosForUniqueWorkFlow(StatementUpdateJob.MANUAL_WORK_NAME),
        workManager.getWorkInfosForUniqueWorkFlow(StatementUpdateJob.REPARSE_WORK_NAME),
    ) { periodic, manual, reparse ->
        val running = (periodic + manual + reparse).filter { it.state == WorkInfo.State.RUNNING }
        if (running.isEmpty()) {
            null
        } else {
            running.sumOf { it.progress.getInt(StatementUpdateJob.PROGRESS_IMPORTED, 0) }
        }
    }.distinctUntilChanged()
}
