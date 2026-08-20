package dev.achmad.finbox.core.update.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import dev.achmad.finbox.BuildConfig
import dev.achmad.finbox.R
import java.io.File

/**
 * Every notification an app update goes through: the offer, the download it runs
 * behind, and the install prompt at the end.
 *
 * The download notification is the foreground one of [AppUpdateDownloadJob], so
 * WorkManager takes it away when the job ends — the install prompt has to be a
 * separate notification to survive that.
 */
class AppUpdateNotifier(private val context: Context) {

    private val manager = context.getSystemService<NotificationManager>()

    fun createChannel() {
        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.app_updates),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    /**
     * Offers the update: the tap downloads it, the release page is an action.
     *
     * A release published without an APK can only be opened, so then the tap is
     * the release page instead.
     */
    fun promptUpdate(update: AppUpdate) {
        createChannel()
        val releaseIntent = openReleaseIntent(update.releaseUrl)
        val downloadIntent = update.apkUrl?.let {
            AppUpdateReceiver.downloadIntent(context, it, update.version, update.releaseUrl)
        }
        val notification = builder()
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.app_update_available, update.version))
            .setContentText(
                context.getString(
                    if (downloadIntent != null) {
                        R.string.app_update_download_hint
                    } else {
                        R.string.app_update_open
                    },
                ),
            )
            .setContentIntent(downloadIntent ?: releaseIntent)
            .setAutoCancel(true)
            .apply {
                if (downloadIntent != null) {
                    addAction(
                        android.R.drawable.stat_sys_download,
                        context.getString(R.string.action_download),
                        downloadIntent,
                    )
                }
                addAction(
                    android.R.drawable.ic_menu_info_details,
                    context.getString(R.string.app_update_release_notes),
                    releaseIntent,
                )
            }
            .build()
        manager?.notify(NOTIFICATION_ID, notification)
    }

    /**
     * The notification the download runs behind, so the job can stay in the foreground.
     *
     * [progress] is negative when the server sent no length to count against, and the
     * bar is left spinning rather than lying about how far along it is.
     */
    fun downloading(version: String?, progress: Int): Notification = builder()
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(
            version
                ?.let { context.getString(R.string.app_update_downloading_version, it) }
                ?: context.getString(R.string.app_update_downloading),
        )
        .setProgress(100, progress.coerceAtLeast(0), progress < 0)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            context.getString(R.string.action_cancel),
            AppUpdateReceiver.cancelIntent(context),
        )
        .build()

    /** Hands the finished APK to the system installer. */
    fun promptInstall(apk: File, version: String?) {
        createChannel()
        val installIntent = installIntent(apk)
        val notification = builder()
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(
                version
                    ?.let { context.getString(R.string.app_update_downloaded_version, it) }
                    ?: context.getString(R.string.app_update_downloaded),
            )
            .setContentText(context.getString(R.string.app_update_install_hint))
            .setContentIntent(installIntent)
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.stat_sys_download_done,
                context.getString(R.string.action_install),
                installIntent,
            )
            .build()
        manager?.notify(INSTALL_NOTIFICATION_ID, notification)
    }

    /** Offers the download again, and the release page as the way round a broken one. */
    fun onDownloadError(update: AppUpdate) {
        createChannel()
        val notification = builder()
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.app_update_download_error))
            .setContentText(context.getString(R.string.app_update_open))
            .setContentIntent(openReleaseIntent(update.releaseUrl))
            .setAutoCancel(true)
            .apply {
                update.apkUrl?.let {
                    addAction(
                        android.R.drawable.stat_sys_download,
                        context.getString(R.string.action_retry),
                        AppUpdateReceiver.downloadIntent(
                            context,
                            it,
                            update.version,
                            update.releaseUrl,
                        ),
                    )
                }
            }
            .build()
        manager?.notify(INSTALL_NOTIFICATION_ID, notification)
    }

    fun cancel() {
        manager?.cancel(NOTIFICATION_ID)
        manager?.cancel(INSTALL_NOTIFICATION_ID)
    }

    private fun builder() = NotificationCompat.Builder(context, CHANNEL_ID)

    private fun openReleaseIntent(url: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * The APK sits in the app's own cache, which the installer cannot read on its
     * own — it goes through the file provider, with read permission granted for
     * this one uri.
     */
    private fun installIntent(apk: File): PendingIntent {
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return PendingIntent.getActivity(
            context,
            INSTALL_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "app_updates"
        const val NOTIFICATION_ID = 1004
        private const val INSTALL_NOTIFICATION_ID = 1005
        private const val APK_MIME = "application/vnd.android.package-archive"
    }
}
