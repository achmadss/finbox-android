package dev.achmad.finbox.core.statement

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.achmad.finbox.core.extension.ExtensionManager
import dev.achmad.finbox.core.util.injectLazy
import java.util.concurrent.TimeUnit

/**
 * Brings every enabled account's statement up to date, on a schedule or on
 * demand, and re-reads stored mail after a parser changes.
 *
 * Both are jobs rather than screen work: a re-parse downloads a body per untried
 * email, which outlives any screen that could start it.
 */
class StatementUpdateJob(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val updater: StatementUpdater by injectLazy()
    private val extensionManager: ExtensionManager by injectLazy()
    private val notifier by lazy { StatementUpdateNotifier(applicationContext) }

    override suspend fun doWork(): Result = try {
        // The registry only fills on reload, and a worker often runs in a process
        // where no screen has done that. Without it the update would download
        // mail no parser is there to read, and pay for it again next time.
        extensionManager.reload()

        val parseOnly = inputData.getBoolean(PARSE_ONLY, false)
        // An import is minutes to hours of paced fetching; a plain worker is
        // stopped after ten. Both long paths run in the foreground so the system
        // lets them finish, and so the user can see why the phone is busy.
        if (parseOnly || updater.isImporting()) {
            notifier.createChannel()
            runCatching { setForeground(foregroundInfo(importedBackTo = null)) }
        }

        val imported = if (parseOnly) {
            updater.parseUnparsed()
        } else {
            updater.updateAll { progress ->
                runCatching { setForeground(foregroundInfo(progress.importedBackTo)) }
            }.getOrThrow()
        }
        if (imported > 0) {
            Result.success(workDataOf("imported" to imported))
        } else {
            Result.success()
        }
    } catch (e: Exception) {
        Result.retry()
    }

    /** Stopping counts as unfinished work, so WorkManager re-runs it and the import resumes. */
    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(importedBackTo = null)

    private fun foregroundInfo(importedBackTo: Long?): ForegroundInfo {
        val notification = notifier.importing(importedBackTo)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                StatementUpdateNotifier.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(StatementUpdateNotifier.NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val WORK_NAME = "finbox_statement_update"
        const val REPARSE_WORK_NAME = "finbox_statement_reparse"

        /** Skip the Gmail sync and only re-read stored mail. */
        private const val PARSE_ONLY = "parse_only"

        /** Every path here talks to Gmail. */
        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StatementUpdateJob>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Manual refresh. A second tap joins the running update rather than racing it. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<StatementUpdateJob>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_manual",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        /**
         * Re-reads stored mail after a parser is installed, updated or enabled.
         *
         * Appended rather than dropped: each change deserves its own pass, and a
         * parser installed while one is running would otherwise be skipped.
         */
        fun reparseNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<StatementUpdateJob>()
                .setConstraints(constraints)
                .setInputData(workDataOf(PARSE_ONLY to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                REPARSE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }
    }
}
