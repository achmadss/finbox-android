package dev.achmad.finbox.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.achmad.finbox.core.di.injectLazy
import java.util.concurrent.TimeUnit

/** Periodic sync worker: imports new transaction emails for all enabled accounts. */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val syncEngine: SyncEngine by injectLazy()

    override suspend fun doWork(): Result = try {
        val processed = syncEngine.syncAll().getOrThrow()
        if (processed > 0) {
            Result.success(workDataOf("processed" to processed))
        } else {
            Result.success()
        }
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        const val WORK_NAME = "finbox_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
