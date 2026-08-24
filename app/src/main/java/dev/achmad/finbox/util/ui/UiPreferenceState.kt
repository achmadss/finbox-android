package dev.achmad.finbox.util.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.achmad.finbox.core.preference.UiPreferences
import dev.achmad.finbox.util.koin.inject
import dev.achmad.finbox.util.preference.collectAsState

/**
 * The clock setting, for the screens that print a time.
 *
 * Each screen reads it for itself rather than a global holding it: the read
 * belongs to the composition that shows the time, so only that screen
 * recomposes when the setting changes.
 */
@Composable
fun rememberUse24HourClock(): Boolean {
    val preferences = remember { inject<UiPreferences>() }
    return preferences.use24HourClock().collectAsState().value
}