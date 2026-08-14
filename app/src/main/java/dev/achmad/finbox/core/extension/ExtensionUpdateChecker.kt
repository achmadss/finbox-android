package dev.achmad.finbox.core.extension

import android.content.Context
import dev.achmad.finbox.util.preference.PreferenceStore
import java.util.concurrent.TimeUnit

/**
 * Fetches the repo index on app start to find out what has a newer build, at
 * most once a day, and says so in a notification.
 *
 * On start rather than on a schedule: an extension update is only worth acting
 * on when the app is in use, and the index is a single small file.
 */
class ExtensionUpdateChecker(
    private val context: Context,
    private val manager: ExtensionManager,
    preferenceStore: PreferenceStore,
) {

    private val lastCheck = preferenceStore.getLong("last_extension_check", 0)

    /** [force] skips the throttle, for a pull-to-refresh the user asked for. */
    suspend fun checkForUpdates(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheck.get() < CHECK_INTERVAL_MILLIS) return

        manager.refreshIndex()
        lastCheck.set(now)
        // The index alone says nothing: what is installed is only known once the
        // APKs on disk have been read, which a cold start has not done yet.
        manager.reload()

        val pending = manager.pendingUpdates()
        val notifier = ExtensionUpdateNotifier(context)
        if (pending.isEmpty()) notifier.dismiss() else notifier.promptUpdates(pending.map { it.name })
    }

    companion object {
        private val CHECK_INTERVAL_MILLIS = TimeUnit.DAYS.toMillis(1)
    }
}
