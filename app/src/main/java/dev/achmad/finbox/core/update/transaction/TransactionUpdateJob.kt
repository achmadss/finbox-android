package dev.achmad.finbox.core.update.transaction

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.achmad.finbox.util.koin.injectLazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Brings every enabled account's transactions up to date, on a schedule or on
 * demand, and re-reads stored mail after a source changes.
 *
 * [TransactionUpdateManager] decides when to run one; this only runs it.
 */
class TransactionUpdateJob(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val updater: TransactionUpdater by injectLazy()
    private val notifier by lazy { TransactionUpdateNotifier(applicationContext) }

    override suspend fun doWork(): Result = try {
        // Held here so a refresh or a re-index is always visibly acknowledged:
        // a run with nothing to do would otherwise finish before the banner shows.
        delay(1.seconds)
        val parseOnly = inputData.getBoolean(TransactionUpdateWork.PARSE_ONLY, false)
        val reparseSourceIds = inputData
            .getStringArray(TransactionUpdateWork.REPARSE_SOURCES)
            ?.toSet()
            .orEmpty()
        val needsForeground = parseOnly || updater.isImporting()
        // Every run can end in a "done" notification, so the channel is made
        // whichever path this is.
        notifier.createChannel()
        // A plain worker is stopped after ten minutes, and an import takes
        // minutes to hours: run in the foreground so the system lets it finish.
        if (needsForeground) {
            runCatching { setForeground(foregroundInfo(imported = 0)) }
        }

        runCatching { setProgress(workDataOf(TransactionUpdateWork.PROGRESS_IMPORTED to 0)) }
        var importedSoFar = 0
        val onProgress: suspend (TransactionUpdater.Progress) -> Unit = { progress ->
            importedSoFar += progress.imported
            runCatching {
                setProgress(workDataOf(TransactionUpdateWork.PROGRESS_IMPORTED to importedSoFar))
            }
            if (needsForeground) {
                runCatching { setForeground(foregroundInfo(importedSoFar)) }
            }
        }
        // Stored mail comes first: re-reading it costs nothing, and a refresh
        // should not ask Gmail for new mail while unparsed mail is lying here.
        // Reparse before unparsed, too: an updated source has both claimed and
        // unreadable mail to look at again.
        var imported = if (reparseSourceIds.isNotEmpty()) {
            updater.reparseSources(reparseSourceIds, onProgress)
        } else {
            0
        }
        imported += updater.parseUnparsed(onProgress)
        if (!parseOnly) {
            imported += updater.updateAll(onProgress).getOrThrow()
        }
        if (imported > 0) {
            // A pull-to-refresh imports without going foreground and is worth
            // announcing too.
            notifier.showDone(imported)
            Result.success(workDataOf("imported" to imported))
        } else {
            Result.success()
        }
    } catch (e: CancellationException) {
        // Being stopped is not a failure, and must not spend a retry.
        throw e
    } catch (e: Exception) {
        Log.e("TransactionUpdate", "Update failed on attempt ${runAttemptCount + 1}", e)
        // Something that fails every time would otherwise retry until the app is uninstalled,
        // and a pending retry is what the manual refresh trips over.
        if (runAttemptCount >= MAX_ATTEMPTS - 1) Result.failure() else Result.retry()
    }

    /** Stopping counts as unfinished work, so WorkManager re-runs it and the import resumes. */
    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(imported = 0)

    private fun foregroundInfo(imported: Int): ForegroundInfo {
        val notification = notifier.importing(imported)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                TransactionUpdateNotifier.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(TransactionUpdateNotifier.NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        /** Attempts before an update gives up until the next schedule or pull. */
        private const val MAX_ATTEMPTS = 3
    }
}
