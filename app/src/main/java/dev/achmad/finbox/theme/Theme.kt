package dev.achmad.finbox.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.achmad.finbox.core.preference.ThemeMode
import dev.achmad.finbox.core.preference.UiPreferences
import dev.achmad.finbox.util.koin.inject
import dev.achmad.finbox.util.preference.collectAsState

/**
 * The whole app's colors, from what Appearance settings say.
 *
 * The preferences are read here rather than passed in: the theme is above every
 * screen, and a screen that changes one has no way to hand the new value up.
 */
@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val preferences = remember { inject<UiPreferences>() }
    val themeMode by preferences.themeMode().collectAsState()
    val amoled by preferences.amoledDark().collectAsState()
    val dynamicColor by preferences.dynamicColor().collectAsState()

    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        // Material You only exists from Android 12; below that the switch has
        // nothing to read and the setting is hidden.
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }.let {
        // Pure black, so an OLED screen can switch those pixels off.
        if (darkTheme && amoled) {
            it.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black)
        } else {
            it
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

@Suppress("DEPRECATION")
@Composable
fun NavigationBarColor(color: Color) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.navigationBarColor = color.toArgb()
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }
}

@Suppress("DEPRECATION")
@Composable
fun StatusBarColor(color: Color) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = color.toArgb()
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }
}