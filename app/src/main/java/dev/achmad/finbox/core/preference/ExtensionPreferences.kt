package dev.achmad.finbox.core.preference

import android.content.Context
import android.os.Build
import dev.achmad.finbox.util.preference.PreferenceStore
import java.util.Locale

/**
 * Which countries' extensions the browse list offers.
 *
 * A bank is national: someone in Jakarta has no use for a list of Malaysian
 * ones. Tachiyomi filters its sources by language for the same reason, and the
 * shape transfers directly — a preference holding the enabled set, a screen
 * editing it, and the list derived by combining the two.
 */
class ExtensionPreferences(
    private val preferenceStore: PreferenceStore,
    private val context: Context,
) {

    /**
     * ISO 3166-1 alpha-2, defaulting to the device's own country.
     *
     * Defaulted rather than empty so a first run shows something. An empty set
     * would be an empty browse list, which reads as a broken index rather than
     * as a filter nobody has set yet.
     */
    fun enabledCountries() =
        preferenceStore.getStringSet("enabled_countries", setOf(deviceCountry()))

    private fun deviceCountry(): String {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        return locale.country.uppercase(Locale.ROOT).ifEmpty { "ID" }
    }
}
