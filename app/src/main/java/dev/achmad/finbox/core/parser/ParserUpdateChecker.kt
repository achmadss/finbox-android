package dev.achmad.finbox.core.parser

import dev.achmad.finbox.core.preference.UpdatePreferences
import dev.achmad.finbox.util.preference.PreferenceStore
import java.util.concurrent.TimeUnit

/**
 * Fetches the repo index on app start, at most once a day: a parser update is
 * only worth acting on when the app is in use, and the index is one small file.
 */
class ParserUpdateChecker(
    private val notifier: ParserUpdateNotifier,
    private val manager: ParserManager,
    private val updatePreferences: UpdatePreferences,
    preferenceStore: PreferenceStore,
) {

    private val lastCheck = preferenceStore.getLong("last_parser_check", 0)

    /** [force] skips the setting and the throttle, for a check the user asked for. */
    suspend fun checkForUpdates(force: Boolean = false) {
        if (!force && !updatePreferences.checkParserUpdates().get()) return
        val now = System.currentTimeMillis()
        if (!force && now - lastCheck.get() < CHECK_INTERVAL_MILLIS) return

        manager.refreshIndex()
        lastCheck.set(now)
        // The index alone says nothing: what is installed is only known once the
        // APKs on disk have been read, which a cold start has not done yet.
        manager.reload()

        val pending = manager.pendingUpdates()
        if (pending.isEmpty()) notifier.dismiss() else notifier.promptUpdates(pending.map { it.name })
    }

    companion object {
        private val CHECK_INTERVAL_MILLIS = TimeUnit.DAYS.toMillis(1)
    }
}
