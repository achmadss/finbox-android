package dev.achmad.finbox.core.statement

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import dev.achmad.finbox.R
import java.text.DateFormat
import java.util.Date

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
    }

    /** Indeterminate progress, labeled with how far back the import has reached. */
    fun importing(importedBackTo: Long?): Notification {
        val text = importedBackTo
            ?.let { context.getString(R.string.statement_update_imported_back_to, formatDate(it)) }
            ?: context.getString(R.string.statement_update_starting)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(context.getString(R.string.statement_update_importing))
            .setContentText(text)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    private fun formatDate(millis: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

    companion object {
        const val CHANNEL_ID = "statement_update"
        const val NOTIFICATION_ID = 1001
    }
}
