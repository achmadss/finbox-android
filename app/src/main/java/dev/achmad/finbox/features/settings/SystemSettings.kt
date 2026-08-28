package dev.achmad.finbox.features.settings

import android.content.Context
import android.content.Intent
import dev.achmad.finbox.R
import dev.achmad.finbox.util.koin.inject
import dev.achmad.finbox.util.ui.ToastHelper

/**
 * Opens a screen the system owns. Guarded: these screens are optional, and a
 * device without the one asked for throws — a settings row must not crash the
 * app.
 */
internal fun Context.openSystemSettings(intent: Intent) {
    runCatching { startActivity(intent) }
        .onFailure { inject<ToastHelper>().show(R.string.error_no_system_screen) }
}
