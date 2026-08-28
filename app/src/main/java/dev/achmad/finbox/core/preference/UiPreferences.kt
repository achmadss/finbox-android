package dev.achmad.finbox.core.preference

import dev.achmad.finbox.util.preference.PreferenceStore
import dev.achmad.finbox.util.preference.getEnum

/** Which colour scheme the app follows. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

class UiPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun themeMode() = preferenceStore.getEnum("theme_mode", ThemeMode.SYSTEM)

    /** Black rather than dark grey, for OLED screens. Only applies to a dark scheme. */
    fun amoledDark() = preferenceStore.getBoolean("amoled_dark", false)

    /** Material You, where the system offers it. Ignored below Android 12. */
    fun dynamicColor() = preferenceStore.getBoolean("dynamic_color", false)

    /** Off shows `2:05 PM` instead of `14:05`. */
    fun use24HourClock() = preferenceStore.getBoolean("use_24_hour_clock", true)

}
