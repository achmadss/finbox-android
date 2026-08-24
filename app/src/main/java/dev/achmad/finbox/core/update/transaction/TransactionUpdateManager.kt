package dev.achmad.finbox.core.update.transaction

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dev.achmad.finbox.R
import dev.achmad.finbox.core.preference.SyncPreferences
import dev.achmad.finbox.util.ui.ToastHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Asks for transaction update work: on a schedule, or now.
 *
 * Nothing here does the updating — [TransactionUpdateJob] does, off in its own
 * process. This is only the side that decides what to enqueue and what to turn
 * away, which is why a screen can hold one without holding a Context.
 */
class TransactionUpdateManager(
    private val workManager: WorkManager,
    private val preferences: SyncPreferences,
    private val toastHelper: ToastHelper,
) {

    private val requestMutex = Mutex()

    /**
     * Applies the fetch schedule the settings ask for, and cancels it when the
     * interval is off.
     *
     * Safe to call again whenever a fetch setting changes: the work is replaced
     * in place, so a new interval or constraint takes effect without waiting out
     * the old period.
     */
    fun schedule() {
        val hours = preferences.autoFetchIntervalHours().get()
        if (hours <= 0) {
            workManager.cancelUniqueWork(TransactionUpdateWork.WORK_NAME)
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
            TransactionUpdateWork.WORK_NAME,
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
    suspend fun runNow(userInitiated: Boolean = true) =
        enqueueOneTime(parseOnly = false, userInitiated = userInitiated)

    /**
     * Re-reads stored mail after a parser is installed, updated or enabled.
     *
     * A parser change while a full update is running is dropped: that update
     * re-reads stored mail on its way through anyway.
     */
    suspend fun reparseNow(userInitiated: Boolean = true) =
        enqueueOneTime(parseOnly = true, userInitiated = userInitiated)

    /**
     * Re-reads the mail these parsers already claimed, after one of their
     * transaction types was switched back on. Those emails are parsed, so
     * [reparseNow] would not look at them.
     */
    suspend fun reparseParsersNow(
        parserIds: Set<Long>,
        userInitiated: Boolean = true,
    ) {
        if (parserIds.isEmpty()) return
        enqueueOneTime(parseOnly = true, parserIds = parserIds, userInitiated = userInitiated)
    }

    private suspend fun enqueueOneTime(
        parseOnly: Boolean,
        parserIds: Set<Long> = emptySet(),
        userInitiated: Boolean = true,
    ) {
        requestMutex.withLock {
            val ongoing = ongoingWork()
            if (ongoing.isNotEmpty() && !supersedes(parseOnly, ongoing.map { it.tags })) {
                // Only the user gets told: the app asking twice by itself — a batch of
                // parsers each wanting a re-read, say — is not something to report.
                if (userInitiated) {
                    toastHelper.show(R.string.transaction_update_ongoing)
                }
                return
            }

            val request = OneTimeWorkRequestBuilder<TransactionUpdateJob>()
                .setConstraints(constraints)
                .apply {
                    if (parseOnly) addTag(TransactionUpdateWork.PARSE_ONLY_TAG)
                    if (parseOnly || parserIds.isNotEmpty()) {
                        setInputData(
                            workDataOf(
                                TransactionUpdateWork.PARSE_ONLY to parseOnly,
                                TransactionUpdateWork.REPARSE_PARSERS to parserIds.toLongArray(),
                            ),
                        )
                    }
                }
                .build()
            // Nothing is running, so anything under this name is a pending retry from a
            // failed run. Replace it: the user asking now beats a queued backoff.
            workManager.enqueueUniqueWork(
                TransactionUpdateWork.MANUAL_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    /**
     * Only work that is actually running blocks a new request. ENQUEUED covers a periodic
     * job waiting for its window and a one-time job waiting out a retry backoff — neither
     * is an update in progress, and treating them as one made the refresh permanently
     * refuse after a single failed run.
     */
    private suspend fun ongoingWork(): List<WorkInfo> {
        val periodic = workManager
            .getWorkInfosForUniqueWorkFlow(TransactionUpdateWork.WORK_NAME)
            .first()
        val oneTime = workManager
            .getWorkInfosForUniqueWorkFlow(TransactionUpdateWork.MANUAL_WORK_NAME)
            .first()
        val legacyReparse = workManager
            .getWorkInfosForUniqueWorkFlow(TransactionUpdateWork.REPARSE_WORK_NAME)
            .first()

        return (periodic + oneTime + legacyReparse)
            .filter { it.state == WorkInfo.State.RUNNING }
    }

    private companion object {
        /** Every path here talks to Gmail. */
        private val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}

/**
 * A full update does everything a parse-only pass does — it re-reads stored mail
 * before it asks Gmail for anything — so it takes one over instead of being turned
 * away by it. Enqueuing replaces the running request, which stops it.
 *
 * Without this an install finishing at the same moment as a refresh could leave the
 * re-read running and the fetch dropped, so nothing new ever arrived.
 *
 * Takes tags rather than the WorkInfo they came off so the rule can be read, and
 * tested, without WorkManager.
 */
internal fun supersedes(parseOnly: Boolean, ongoingTags: List<Set<String>>): Boolean =
    !parseOnly && ongoingTags.all { TransactionUpdateWork.PARSE_ONLY_TAG in it }
