package dev.achmad.finbox.core.preference

import dev.achmad.finbox.util.preference.PreferenceStore

/** What the user has changed about the extensions this build ships. */
class ExtensionPreferences(
    private val preferenceStore: PreferenceStore,
) {

    /**
     * The extensions switched off, by id.
     *
     * Disabled rather than enabled, so an extension added in a later release
     * arrives on rather than invisible. An empty set is the normal state.
     */
    fun disabledExtensions() = preferenceStore.getStringSet("disabled_extensions", emptySet())

    /**
     * The app versionCode whose extensions last read the stored mail.
     *
     * Extensions ship with the app, so this is what says whether any of them
     * changed since the last pass — see [dev.achmad.finbox.core.extension.ExtensionManager.reparseIfAppUpdated].
     */
    fun lastParsedAppVersion() = preferenceStore.getInt("last_parsed_app_version", 0)
}
