package dev.achmad.finbox.core.extension

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import dev.achmad.finbox.MainActivity
import dev.achmad.finbox.R

/** Tells the user an extension has a newer build, and opens the list on tap. */
class ExtensionUpdateNotifier(private val context: Context) {

    private val manager = context.getSystemService<NotificationManager>()

    fun promptUpdates(names: List<String>) {
        createChannel()
        val listed = names.joinToString(", ")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(
                context.resources.getQuantityString(
                    R.plurals.extension_update_available,
                    names.size,
                    names.size,
                ),
            )
            .setContentText(listed)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listed))
            .setContentIntent(openExtensionsIntent())
            .setAutoCancel(true)
            .build()
        manager?.notify(NOTIFICATION_ID, notification)
    }

    fun dismiss() {
        manager?.cancel(NOTIFICATION_ID)
    }

    private fun createChannel() {
        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.extension_updates),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    private fun openExtensionsIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_EXTENSIONS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_OPEN_EXTENSIONS = "dev.achmad.finbox.OPEN_EXTENSIONS"
        private const val CHANNEL_ID = "extension_updates"
        private const val NOTIFICATION_ID = 1003
    }
}
