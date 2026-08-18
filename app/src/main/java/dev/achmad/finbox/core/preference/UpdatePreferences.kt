package dev.achmad.finbox.core.preference

import dev.achmad.finbox.util.preference.PreferenceStore

/** Whether the app looks for newer builds of itself and of its parsers. */
class UpdatePreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun checkParserUpdates() = preferenceStore.getBoolean("check_parser_updates", true)

    fun checkAppUpdates() = preferenceStore.getBoolean("check_app_updates", true)

    /** Throttles the automatic app check to once a day. */
    fun lastAppUpdateCheck() = preferenceStore.getLong("last_app_update_check", 0)
}
