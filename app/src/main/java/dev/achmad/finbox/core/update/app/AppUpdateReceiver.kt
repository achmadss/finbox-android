package dev.achmad.finbox.core.update.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The notification buttons an app update offers.
 *
 * A broadcast rather than an activity: starting or stopping a download has no
 * screen to open, and opening one to do it would only get in the way.
 */
class AppUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DOWNLOAD -> {
                val url = intent.getStringExtra(AppUpdateDownloadJob.EXTRA_URL) ?: return
                AppUpdateDownloadJob.start(
                    context = context,
                    url = url,
                    version = intent.getStringExtra(AppUpdateDownloadJob.EXTRA_VERSION).orEmpty(),
                    releaseUrl = intent.getStringExtra(AppUpdateDownloadJob.EXTRA_RELEASE_URL)
                        .orEmpty(),
                )
            }
            ACTION_CANCEL -> {
                AppUpdateDownloadJob.stop(context)
                AppUpdateNotifier(context).cancel()
            }
        }
    }

    companion object {
        private const val ACTION_DOWNLOAD = "dev.achmad.finbox.DOWNLOAD_APP_UPDATE"
        private const val ACTION_CANCEL = "dev.achmad.finbox.CANCEL_APP_UPDATE"

        fun downloadIntent(
            context: Context,
            url: String,
            version: String,
            releaseUrl: String,
        ): PendingIntent {
            val intent = Intent(context, AppUpdateReceiver::class.java)
                .setAction(ACTION_DOWNLOAD)
                .putExtra(AppUpdateDownloadJob.EXTRA_URL, url)
                .putExtra(AppUpdateDownloadJob.EXTRA_VERSION, version)
                .putExtra(AppUpdateDownloadJob.EXTRA_RELEASE_URL, releaseUrl)
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun cancelIntent(context: Context): PendingIntent {
            val intent = Intent(context, AppUpdateReceiver::class.java).setAction(ACTION_CANCEL)
            return PendingIntent.getBroadcast(
                context,
                1,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
