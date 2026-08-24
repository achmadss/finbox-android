package dev.achmad.finbox.core.preference

import dev.achmad.finbox.util.preference.Preference
import dev.achmad.finbox.util.preference.PreferenceStore

/**
 * Which of a parser's transaction methods the user has switched off.
 *
 * Stored as the off ones rather than the on ones so a method a parser adds in
 * a later version arrives switched on — the alternative is a new method silently
 * doing nothing until the user goes looking for it.
 *
 * Keyed by package, not by parser id: a parser id has the version code in it,
 * so keying on that would reset every switch each time the parser updates.
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

    /** Forgets an uninstalled parser's switches rather than leaving them to rot. */
    fun clear(pkg: String) = disabled(pkg).delete()
}
