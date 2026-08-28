package dev.achmad.finbox.util.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.achmad.finbox.core.preference.UiPreferences
import dev.achmad.finbox.util.koin.inject
import dev.achmad.finbox.util.preference.collectAsState

/**
 * The clock setting. Each screen that prints a time reads it itself, so only
 * the composition showing one recomposes when it changes.
 */
@Composable
fun rememberUse24HourClock(): Boolean {
    val preferences = remember { inject<UiPreferences>() }
    return preferences.use24HourClock().collectAsState().value
}