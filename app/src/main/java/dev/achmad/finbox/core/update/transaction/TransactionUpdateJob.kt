package dev.achmad.finbox.core.update.transaction

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.achmad.finbox.core.parser.ParserManager
import dev.achmad.finbox.util.koin.injectLazy
import kotlinx.coroutines.CancellationException

/**
 * Brings every enabled account's transactions up to date, on a schedule or on
 * demand, and re-reads stored mail after a parser changes.
 *
 * Both are jobs rather than screen work: a re-parse downloads a body per untried
 * email, which outlives any screen that could start it. What decides to run one
 * is [TransactionUpdateManager]; this only runs it.
 */
class TransactionUpdateJob(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val updater: TransactionUpdater by injectLazy()
    private val parserManager: ParserManager by injectLazy()
    private val notifier by lazy { TransactionUpdateNotifier(applicationContext) }

    override suspend fun doWork(): Result = try {
        // The registry only fills on reload, and a worker often runs in a process
        // where no screen has done that. Without it the update would download
        // mail no parser is there to read, and pay for it again next time.
        parserManager.reload()

        val parseOnly = inputData.getBoolean(TransactionUpdateWork.PARSE_ONLY, false)
        val reparseParserIds = inputData
            .getLongArray(TransactionUpdateWork.REPARSE_PARSERS)
            ?.toSet()
            .orEmpty()
        val needsForeground = parseOnly || updater.isImporting()
        // An import is minutes to hours of paced fetching; a plain worker is
        // stopped after ten. Both long paths run in the foreground so the system
        // lets them finish, and so the user can see why the phone is busy.
        if (needsForeground) {
            notifier.createChannel()
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
        // Stored mail comes first, always. A parser installed or updated
        // since the last run can read emails already sitting here, and reading
        // them again costs nothing now that their bodies are stored — so there
        // is no reason for a refresh to ask Gmail for new mail while old mail
        // nothing could parse is still lying unparsed.
        var imported = if (reparseParserIds.isNotEmpty()) {
            updater.reparseParsers(reparseParserIds, onProgress)
        } else {
            updater.parseUnparsed(onProgress)
        }
        if (!parseOnly) {
            imported += updater.updateAll(onProgress).getOrThrow()
        }
        if (needsForeground) {
            notifier.showDone(imported)
        }
        if (imported > 0) {
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
