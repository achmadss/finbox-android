package dev.achmad.finbox.core.update.transaction

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import dev.achmad.finbox.MainActivity
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
        // Without this, tapping the only notification an import shows does nothing.
        contentIntent = openTransactionsIntent(),
    )

    /**
     * Posts a separate notification so WorkManager cannot remove it with the foreground one.
     *
     * This one is worth tapping — the transactions it announces are in the list — so it
     * carries an intent onto that list, and dismisses itself once tapped.
     */
    fun showDone(imported: Int) {
        manager?.notify(
            DONE_NOTIFICATION_ID,
            notification(
                title = context.getString(R.string.transaction_update_done_title),
                text = context.getString(R.string.transaction_update_done, imported),
                ongoing = false,
                indeterminate = false,
                contentIntent = openTransactionsIntent(),
                autoCancel = true,
            ),
        )
    }

    private fun notification(
        title: String,
        text: String,
        ongoing: Boolean,
        indeterminate: Boolean,
        contentIntent: PendingIntent? = null,
        autoCancel: Boolean = false,
    ): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(0, 0, indeterminate)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(contentIntent)
            .setAutoCancel(autoCancel)
            .build()

    /**
     * CLEAR_TOP so a tap lands on the app as it already is rather than stacking a
     * second copy of it, and the action tells [MainActivity] to show the list.
     */
    private fun openTransactionsIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_TRANSACTIONS)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    // SINGLE_TOP so a running app is brought forward and told through
                    // onNewIntent, rather than torn down and built again.
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        return PendingIntent.getActivity(
            context,
            DONE_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_OPEN_TRANSACTIONS = "dev.achmad.finbox.OPEN_TRANSACTIONS"
        const val CHANNEL_ID = "transaction_update"
        const val NOTIFICATION_ID = 1001
        private const val DONE_NOTIFICATION_ID = 1002
    }
}
