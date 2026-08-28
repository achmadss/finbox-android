package dev.achmad.finbox.core.preference

import dev.achmad.finbox.util.preference.Preference
import dev.achmad.finbox.util.preference.PreferenceStore

/**
 * The transaction methods the user has switched off.
 *
 * Stored as the off ones so a method a parser adds later arrives switched on;
 * keyed by package, not parser id, since the id encodes the version code and an
 * update would reset every switch.
 */
class ParserMethodPreference(
    private val preferenceStore: PreferenceStore,
) {

    fun disabled(pkg: String): Preference<Set<String>> =
        preferenceStore.getStringSet("parser_disabled_methods_$pkg")

    fun isEnabled(pkg: String, key: String): Boolean = key !in disabled(pkg).get()

    /** @return true if the method is now on. */
    fun toggle(pkg: String, key: String): Boolean {
        val preference = disabled(pkg)
        val current = preference.get()
        val enabling = key in current
        preference.set(if (enabling) current - key else current + key)
        return enabling
    }

    fun clear(pkg: String) = disabled(pkg).delete()
}
