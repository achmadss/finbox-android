package dev.achmad.finbox.core.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import dev.achmad.finbox.BuildConfig
import dev.achmad.finbox.R
import dev.achmad.finbox.core.FinboxConfig
import dev.achmad.finbox.core.preference.UpdatePreferences
import dev.achmad.finbox.util.network.get
import dev.achmad.finbox.util.network.parseAs
import java.util.concurrent.TimeUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.CacheControl
import okhttp3.OkHttpClient

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
)

/** A newer build than this one, as published on GitHub. */
data class AppUpdate(val version: String, val releaseUrl: String)

/**
 * Looks for a newer release of the app itself.
 *
 * The releases page is the source: this app is installed from an APK, so no
 * store is watching for it. A check is a single small request, throttled to
 * once a day on start and unthrottled when the user asks in settings.
 */
class AppUpdateChecker(
    private val context: Context,
    private val client: OkHttpClient,
    private val preferences: UpdatePreferences,
) {

    /**
     * @param force skips both the preference and the throttle, for a check the
     *   user asked for.
     * @return the newer release, or null when this build is current.
     */
    suspend fun checkForUpdate(force: Boolean = false): AppUpdate? {
        if (!force) {
            if (!preferences.checkAppUpdates().get()) return null
            val now = System.currentTimeMillis()
            if (now - preferences.lastAppUpdateCheck().get() < CHECK_INTERVAL_MILLIS) return null
        }

        val release = client.get(
            url = FinboxConfig.APP_RELEASES_URL,
            cacheControl = CacheControl.FORCE_NETWORK,
        ).parseAs<GithubRelease>()
        // Marked only once an answer is in, so a check cut short by a closing app
        // or a dead network does not cost the day's attempt.
        if (!force) preferences.lastAppUpdateCheck().set(System.currentTimeMillis())

        val latest = release.tagName.removePrefix("v").trim()
        if (latest.isEmpty() || !isNewerThanInstalled(latest)) return null
        return AppUpdate(version = latest, releaseUrl = release.htmlUrl)
    }

    /** The same check, with the answer delivered as a notification. */
    suspend fun checkAndNotify() {
        val update = runCatching { checkForUpdate() }.getOrNull() ?: return
        AppUpdateNotifier(context).promptUpdate(update)
    }

    /**
     * Compares dotted versions a number at a time, so 1.10 beats 1.9.
     *
     * Anything that isn't a number reads as 0: a tag the app cannot parse must
     * not be announced as an update.
     */
    private fun isNewerThanInstalled(latest: String): Boolean {
        val installed = BuildConfig.VERSION_NAME.removePrefix("v").trim()
        val latestParts = latest.split(".")
        val installedParts = installed.split(".")
        for (i in 0 until maxOf(latestParts.size, installedParts.size)) {
            val l = latestParts.getOrNull(i)?.toIntOrNull() ?: 0
            val installedPart = installedParts.getOrNull(i)?.toIntOrNull() ?: 0
            if (l != installedPart) return l > installedPart
        }
        return false
    }

    private companion object {
        val CHECK_INTERVAL_MILLIS = TimeUnit.DAYS.toMillis(1)
    }
}

/** Tells the user a newer build exists, and opens its release page on tap. */
class AppUpdateNotifier(private val context: Context) {

    private val manager = context.getSystemService<NotificationManager>()

    fun promptUpdate(update: AppUpdate) {
        createChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.app_update_available, update.version))
            .setContentText(context.getString(R.string.app_update_open))
            .setContentIntent(openReleaseIntent(update.releaseUrl))
            .setAutoCancel(true)
            .build()
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.app_update_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

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

    private companion object {
        const val CHANNEL_ID = "app_updates"
        const val NOTIFICATION_ID = 1004
    }
}
