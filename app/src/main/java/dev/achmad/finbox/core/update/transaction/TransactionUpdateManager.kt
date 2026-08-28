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
import dev.achmad.finbox.core.preference.OnboardingPreference
import dev.achmad.finbox.core.preference.SyncPreferences
import dev.achmad.finbox.util.ui.ToastHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Asks for transaction update work: on a schedule, or now.
 *
 * [TransactionUpdateJob] runs the update; this side only decides what to
 * enqueue and what to turn away.
 */
class TransactionUpdateManager(
    private val workManager: WorkManager,
    private val preferences: SyncPreferences,
    private val onboardingPreference: OnboardingPreference,
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
        // Before onboarding finishes there is no account to fetch from, so a schedule
        // would only run empty. Onboarding calls this itself on its way out.
        if (hours <= 0 || !onboardingPreference.onboardingComplete().get()) {
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
            // A periodic request runs its first period straight away, so without this a
            // fresh install syncs before it has an account to sync — and flashes the
            // banner on the very first launch.
            .setInitialDelay(hours.toLong(), TimeUnit.HOURS)
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
     * Re-reads stored mail.
     *
     * [includeParsed] decides how much: off, only mail nothing has claimed yet —
     * what a plain refresh wants. On, also the mail [sourceIds] already claimed,
     * what an updated source needs.
     *
     * Nearly free: the bodies are already here, and the rows come back under the
     * same ids, so a re-read updates them in place rather than duplicating them.
     */
    suspend fun reparseNow(
        includeParsed: Boolean = false,
        sourceIds: Set<String> = emptySet(),
        userInitiated: Boolean = true,
    ) = enqueueOneTime(
        parseOnly = true,
        sourceIds = if (includeParsed) sourceIds else emptySet(),
        userInitiated = userInitiated,
    )

    /**
     * Re-reads the mail these sources already claimed, after one of their
     * transaction methods was switched back on. Those emails are parsed, so
     * [reparseNow] would not look at them.
     */
    suspend fun reparseSourcesNow(
        sourceIds: Set<String>,
        userInitiated: Boolean = true,
    ) {
        if (sourceIds.isEmpty()) return
        enqueueOneTime(parseOnly = true, sourceIds = sourceIds, userInitiated = userInitiated)
    }

    private suspend fun enqueueOneTime(
        parseOnly: Boolean,
        sourceIds: Set<String> = emptySet(),
        userInitiated: Boolean = true,
    ) {
        requestMutex.withLock {
            val ongoing = ongoingWork()
            if (ongoing.isNotEmpty() && !supersedes(parseOnly, ongoing.map { it.tags })) {
                // Only the user gets told: the app asking twice by itself — a batch of
                // sources each wanting a re-read, say — is not something to report.
                if (userInitiated) {
                    toastHelper.show(R.string.transaction_update_ongoing)
                }
                return
            }

            val request = OneTimeWorkRequestBuilder<TransactionUpdateJob>()
                .setConstraints(constraints)
                .apply {
                    if (parseOnly) addTag(TransactionUpdateWork.PARSE_ONLY_TAG)
                    if (parseOnly || sourceIds.isNotEmpty()) {
                        setInputData(
                            workDataOf(
                                TransactionUpdateWork.PARSE_ONLY to parseOnly,
                                TransactionUpdateWork.REPARSE_SOURCES to sourceIds.toTypedArray(),
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
     * Only genuinely running work blocks a new request. A periodic job waiting
     * for its window or a one-time job in retry backoff is not an update in
     * progress.
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
 * A full update does everything a parse-only pass does, so it takes one over
 * instead of being turned away by it. Without this an install finishing at the
 * same moment as a refresh could leave the re-read running and the fetch
 * dropped.
 */
internal fun supersedes(parseOnly: Boolean, ongoingTags: List<Set<String>>): Boolean =
    !parseOnly && ongoingTags.all { TransactionUpdateWork.PARSE_ONLY_TAG in it }
