package dev.achmad.finbox.core.update.app

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.achmad.finbox.util.koin.injectLazy
import dev.achmad.finbox.util.network.get
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Downloads the APK of a newer release and offers it to the system installer.
 *
 * A job rather than screen work: the download outlives the notification tap that
 * started it, and the app may not even be open then.
 */
class AppUpdateDownloadJob(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val client: OkHttpClient by injectLazy()
    private val notifier by lazy { AppUpdateNotifier(context) }

    private val update: AppUpdate
        get() = AppUpdate(
            version = inputData.getString(EXTRA_VERSION).orEmpty(),
            releaseUrl = inputData.getString(EXTRA_RELEASE_URL).orEmpty(),
            apkUrl = inputData.getString(EXTRA_URL),
        )

    override suspend fun doWork(): Result {
        val url = inputData.getString(EXTRA_URL)
        if (url.isNullOrEmpty()) return Result.failure()

        notifier.createChannel()
        // The offer has been taken; the progress notification replaces it.
        notifier.cancel()
        runCatching { setForeground(foregroundInfo(progress = -1)) }

        return try {
            val apk = withContext(Dispatchers.IO) { download(url) }
            notifier.promptInstall(apk, update.version.takeIf { it.isNotEmpty() })
            Result.success()
        } catch (e: CancellationException) {
            // Cancelled by the user, or stopped by the system: no notification to leave behind.
            notifier.cancel()
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "App update download failed", e)
            notifier.onDownloadError(update)
            // Retrying by itself would download tens of megabytes again on a phone that
            // may be on mobile data. The notification offers it instead.
            Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(progress = -1)

    /**
     * Writes to the external cache, which is where a file readable through the
     * provider by the installer can live, and where the system can reclaim it.
     */
    private suspend fun download(url: String): File {
        val apk = File(context.externalCacheDir ?: context.cacheDir, APK_NAME)
        // A part-written file from a failed attempt would otherwise be installed.
        apk.delete()

        // No cache control: an APK would evict everything else from the shared cache.
        client.get(url, cacheControl = null).use { response ->
            val total = response.body.contentLength()
            response.body.byteStream().use { input ->
                apk.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    var lastProgress = -1
                    var lastTick = 0L
                    while (true) {
                        // A blocking read cannot be interrupted, so cancellation is
                        // noticed here, one buffer at a time.
                        if (isStopped) throw CancellationException("Download stopped")
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        if (total <= 0) continue
                        val progress = (downloaded * 100 / total).toInt()
                        val now = System.currentTimeMillis()
                        // Every percent would post hundreds of notifications on a fast
                        // connection, and the system starts dropping them.
                        if (progress > lastProgress && now - lastTick > PROGRESS_INTERVAL_MILLIS) {
                            lastProgress = progress
                            lastTick = now
                            runCatching { setForeground(foregroundInfo(progress)) }
                        }
                    }
                }
            }
        }
        return apk
    }

    private fun foregroundInfo(progress: Int): ForegroundInfo {
        val notification = notifier.downloading(
            version = inputData.getString(EXTRA_VERSION)?.takeIf { it.isNotEmpty() },
            progress = progress,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                AppUpdateNotifier.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(AppUpdateNotifier.NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "AppUpdateDownload"
        private const val APK_NAME = "update.apk"
        private const val PROGRESS_INTERVAL_MILLIS = 200L

        const val EXTRA_URL = "download_url"
        const val EXTRA_VERSION = "download_version"
        const val EXTRA_RELEASE_URL = "release_url"

        /** Replaces any download already running: only the newest offer is worth finishing. */
        fun start(context: Context, url: String, version: String, releaseUrl: String) {
            val request = OneTimeWorkRequestBuilder<AppUpdateDownloadJob>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setInputData(
                    workDataOf(
                        EXTRA_URL to url,
                        EXTRA_VERSION to version,
                        EXTRA_RELEASE_URL to releaseUrl,
                    ),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(TAG)
        }
    }
}
