package dev.achmad.finbox.core.update.transaction

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.achmad.finbox.R
import dev.achmad.finbox.core.parser.ParserManager
import dev.achmad.finbox.core.preference.SyncPreferences
import dev.achmad.finbox.util.koin.inject
import dev.achmad.finbox.util.koin.injectLazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Brings every enabled account's transactions up to date, on a schedule or on
 * demand, and re-reads stored mail after a parser changes.
 *
 * Both are jobs rather than screen work: a re-parse downloads a body per untried
 * email, which outlives any screen that could start it.
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

        val parseOnly = inputData.getBoolean(PARSE_ONLY, false)
        val reparseSourceIds = inputData.getLongArray(REPARSE_SOURCES)?.toSet().orEmpty()
        val needsForeground = parseOnly || updater.isImporting()
        // An import is minutes to hours of paced fetching; a plain worker is
        // stopped after ten. Both long paths run in the foreground so the system
        // lets them finish, and so the user can see why the phone is busy.
        if (needsForeground) {
            notifier.createChannel()
            runCatching { setForeground(foregroundInfo(imported = 0)) }
        }

        runCatching { setProgress(workDataOf(PROGRESS_IMPORTED to 0)) }
        var importedSoFar = 0
        val onProgress: suspend (TransactionUpdater.Progress) -> Unit = { progress ->
            importedSoFar += progress.imported
            runCatching { setProgress(workDataOf(PROGRESS_IMPORTED to importedSoFar)) }
            if (needsForeground) {
                runCatching { setForeground(foregroundInfo(importedSoFar)) }
            }
        }
        // Stored mail comes first, always. A parser installed or updated
        // since the last run can read emails already sitting here, and reading
        // them again costs nothing now that their bodies are stored — so there
        // is no reason for a refresh to ask Gmail for new mail while old mail
        // nothing could parse is still lying unparsed.
        var imported = if (reparseSourceIds.isNotEmpty()) {
            updater.reparseSource(reparseSourceIds, onProgress)
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

    companion object {
        const val WORK_NAME = "finbox_transaction_update"
        /** Shared by manual and parse-only requests so they cannot overlap. */
        const val MANUAL_WORK_NAME = "${WORK_NAME}_manual"
        const val REPARSE_WORK_NAME = "finbox_transaction_reparse"
        const val PROGRESS_IMPORTED = "progress_imported"

        /** Attempts before an update gives up until the next schedule or pull. */
        private const val MAX_ATTEMPTS = 3

        /** Skip the Gmail sync and only re-read stored mail. */
        private const val PARSE_ONLY = "parse_only"

        /** Source ids whose already-claimed mail is to be read again. */
        private const val REPARSE_SOURCES = "reparse_sources"

        /** On a request that only re-reads stored mail — what a full update may supersede. */
        private const val PARSE_ONLY_TAG = "parse_only_request"

        /** Every path here talks to Gmail. */
        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Applies the fetch schedule the settings ask for, and cancels it when the
         * interval is off.
         *
         * Safe to call again whenever a fetch setting changes: the work is replaced
         * in place, so a new interval or constraint takes effect without waiting out
         * the old period.
         */
        fun schedule(context: Context) {
            val preferences = inject<SyncPreferences>()
            val workManager = WorkManager.getInstance(context)
            val hours = preferences.autoFetchIntervalHours().get()
            if (hours <= 0) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }

            val scheduleConstraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (preferences.fetchOnUnmeteredOnly().get()) {
                        NetworkType.UNMETERED
                    } else {
                        NetworkType.CONNECTED
                    },
                )
                .setRequiresCharging(preferences.fetchWhenChargingOnly().get())
                .setRequiresBatteryNotLow(preferences.fetchWhenBatteryNotLow().get())
                .build()

            val request = PeriodicWorkRequestBuilder<TransactionUpdateJob>(hours.toLong(), TimeUnit.HOURS)
                .setConstraints(scheduleConstraints)
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        /**
         * Full refresh: stored mail first, then Gmail.
         *
         * [userInitiated] is what separates a pull-to-refresh from a step the app
         * took by itself — only the former says so when the request is turned down.
         */
        suspend fun runNow(context: Context, userInitiated: Boolean = true) =
            enqueueOneTime(context, parseOnly = false, userInitiated = userInitiated)

        /**
         * Re-reads stored mail after a parser is installed, updated or enabled.
         *
         * A parser change while a full update is running is dropped: that update
         * re-reads stored mail on its way through anyway.
         */
        suspend fun reparseNow(context: Context, userInitiated: Boolean = true) =
            enqueueOneTime(context, parseOnly = true, userInitiated = userInitiated)

        /**
         * Re-reads the mail these sources already claimed, after one of their
         * transaction kinds was switched back on. Those emails are parsed, so
         * [reparseNow] would not look at them.
         */
        suspend fun reparseSourcesNow(
            context: Context,
            sourceIds: Set<Long>,
            userInitiated: Boolean = true,
        ) {
            if (sourceIds.isEmpty()) return
            enqueueOneTime(context, parseOnly = true, sourceIds = sourceIds, userInitiated = userInitiated)
        }

        private suspend fun enqueueOneTime(
            context: Context,
            parseOnly: Boolean,
            sourceIds: Set<Long> = emptySet(),
            userInitiated: Boolean = true,
        ) {
            requestMutex.withLock {
                val workManager = WorkManager.getInstance(context)
                val ongoing = ongoingWork(workManager)
                if (ongoing.isNotEmpty() && !supersedes(parseOnly, ongoing)) {
                    // Only the user gets told: the app asking twice by itself — a batch of
                    // parsers each wanting a re-read, say — is not something to report.
                    if (userInitiated) {
                        Toast.makeText(
                            context.applicationContext,
                            context.getString(R.string.transaction_update_ongoing),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    return
                }

                val request = OneTimeWorkRequestBuilder<TransactionUpdateJob>()
                    .setConstraints(constraints)
                    .apply {
                        if (parseOnly) addTag(PARSE_ONLY_TAG)
                        if (parseOnly || sourceIds.isNotEmpty()) {
                            setInputData(
                                workDataOf(
                                    PARSE_ONLY to parseOnly,
                                    REPARSE_SOURCES to sourceIds.toLongArray(),
                                ),
                            )
                        }
                    }
                    .build()
                // Nothing is running, so anything under this name is a pending retry from a
                // failed run. Replace it: the user asking now beats a queued backoff.
                workManager.enqueueUniqueWork(
                    MANUAL_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
        }

        /**
         * A full update does everything a parse-only pass does — it re-reads stored mail
         * before it asks Gmail for anything — so it takes one over instead of being turned
         * away by it. Enqueuing replaces the running request, which stops it.
         *
         * Without this an install finishing at the same moment as a refresh could leave the
         * re-read running and the fetch dropped, so nothing new ever arrived.
         */
        private fun supersedes(parseOnly: Boolean, ongoing: List<WorkInfo>): Boolean =
            !parseOnly && ongoing.all { PARSE_ONLY_TAG in it.tags }

        /**
         * Only work that is actually running blocks a new request. ENQUEUED covers a periodic
         * job waiting for its window and a one-time job waiting out a retry backoff — neither
         * is an update in progress, and treating them as one made the refresh permanently
         * refuse after a single failed run.
         */
        private suspend fun ongoingWork(workManager: WorkManager): List<WorkInfo> {
            val periodic = workManager
                .getWorkInfosForUniqueWorkFlow(WORK_NAME)
                .first()
            val oneTime = workManager
                .getWorkInfosForUniqueWorkFlow(MANUAL_WORK_NAME)
                .first()
            val legacyReparse = workManager
                .getWorkInfosForUniqueWorkFlow(REPARSE_WORK_NAME)
                .first()

            return (periodic + oneTime + legacyReparse)
                .filter { it.state == WorkInfo.State.RUNNING }
        }

        private val requestMutex = Mutex()
    }
}
