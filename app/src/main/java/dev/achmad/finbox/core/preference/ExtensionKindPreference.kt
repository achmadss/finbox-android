package dev.achmad.finbox.core.preference

import dev.achmad.finbox.util.preference.Preference
import dev.achmad.finbox.util.preference.PreferenceStore

/**
 * Which of an extension's transaction kinds the user has switched off.
 *
 * Stored as the off ones rather than the on ones so a kind an extension adds in
 * a later version arrives switched on — the alternative is a new kind silently
 * doing nothing until the user goes looking for it.
 *
 * Keyed by package, not by source id: a source id has the version code in it,
 * so keying on that would reset every switch each time the extension updates.
 */
class ExtensionKindPreference(
    private val preferenceStore: PreferenceStore,
) {

    fun disabled(pkg: String): Preference<Set<String>> =
        preferenceStore.getStringSet("extension_disabled_kinds_$pkg")

    fun isEnabled(pkg: String, key: String): Boolean = key !in disabled(pkg).get()

    /** @return true if the kind is now on. */
    fun toggle(pkg: String, key: String): Boolean {
        val preference = disabled(pkg)
        val current = preference.get()
        val enabling = key in current
        preference.set(if (enabling) current - key else current + key)
        return enabling
    }

    /** Forgets an uninstalled extension's switches rather than leaving them to rot. */
    fun clear(pkg: String) = disabled(pkg).delete()
}
