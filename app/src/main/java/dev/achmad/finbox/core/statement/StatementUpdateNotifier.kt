package dev.achmad.finbox.core.statement

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import dev.achmad.finbox.R

/**
 * The notification an import runs behind.
 *
 * A first import can take far longer than the ten minutes a background worker
 * is given, so it runs as a foreground service — which Android only allows
 * with something visible attached.
 */
class StatementUpdateNotifier(private val context: Context) {

    private val manager = context.getSystemService<NotificationManager>()

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.statement_update_channel),
            // Low: an import is long and uninteresting, it shouldn't make noise.
            NotificationManager.IMPORTANCE_LOW,
        )
        manager?.createNotificationChannel(channel)
        manager?.cancel(DONE_NOTIFICATION_ID)
    }

    /** Indeterminate progress, labeled with the transactions imported so far. */
    fun importing(imported: Int): Notification = notification(
        context.getString(R.string.statement_update_importing_progress, imported),
        ongoing = true,
        indeterminate = true,
    )

    /** Posts a separate notification so WorkManager cannot remove it with the foreground one. */
    fun showDone(imported: Int) {
        manager?.notify(
            DONE_NOTIFICATION_ID,
            notification(
                context.getString(R.string.statement_update_done, imported),
                ongoing = false,
                indeterminate = false,
            ),
        )
    }

    private fun notification(text: String, ongoing: Boolean, indeterminate: Boolean): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(context.getString(R.string.statement_update_importing))
            .setContentText(text)
            .setProgress(0, 0, indeterminate)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

    companion object {
        const val CHANNEL_ID = "statement_update"
        const val NOTIFICATION_ID = 1001
        private const val DONE_NOTIFICATION_ID = 1002
    }
}
