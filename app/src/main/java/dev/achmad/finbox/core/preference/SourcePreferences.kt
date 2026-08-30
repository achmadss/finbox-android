package dev.achmad.finbox.core.preference

import dev.achmad.finbox.util.preference.PreferenceStore

/** What the user has changed about the sources this build ships. */
class SourcePreferences(
    private val preferenceStore: PreferenceStore,
) {

    /**
     * The sources switched off, by id.
     *
     * Disabled rather than enabled, so a source added in a later release
     * arrives on rather than invisible. An empty set is the normal state.
     */
    fun disabledSources() = preferenceStore.getStringSet("disabled_sources", emptySet())

    /**
     * The app versionCode whose sources last read the stored mail.
     *
     * Sources ship with the app, so this is what says whether any of them
     * changed since the last pass — see [dev.achmad.finbox.core.source.SourceManager.reparseIfAppUpdated].
     */
    fun lastParsedAppVersion() = preferenceStore.getInt("last_parsed_app_version", 0)
}
