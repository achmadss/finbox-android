package dev.achmad.finbox.core.update.transaction

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
class TransactionUpdateNotifier(private val context: Context) {

    private val manager = context.getSystemService<NotificationManager>()

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.transaction_update_channel),
            // Low: an import is long and uninteresting, it shouldn't make noise.
            NotificationManager.IMPORTANCE_LOW,
        )
        manager?.createNotificationChannel(channel)
        manager?.cancel(DONE_NOTIFICATION_ID)
    }

    /** Indeterminate progress, labeled with the transactions imported so far. */
    fun importing(imported: Int): Notification = notification(
        title = context.getString(R.string.transaction_update_importing),
        text = context.getString(R.string.transaction_update_importing_progress, imported),
        ongoing = true,
        indeterminate = true,
    )

    /** Posts a separate notification so WorkManager cannot remove it with the foreground one. */
    fun showDone(imported: Int) {
        manager?.notify(
            DONE_NOTIFICATION_ID,
            notification(
                title = context.getString(R.string.transaction_update_done_title),
                text = context.getString(R.string.transaction_update_done, imported),
                ongoing = false,
                indeterminate = false,
            ),
        )
    }

    private fun notification(
        title: String,
        text: String,
        ongoing: Boolean,
        indeterminate: Boolean,
    ): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(0, 0, indeterminate)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

    companion object {
        const val CHANNEL_ID = "transaction_update"
        const val NOTIFICATION_ID = 1001
        private const val DONE_NOTIFICATION_ID = 1002
    }
}
