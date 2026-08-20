package dev.achmad.finbox.features.settings.language

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.achmad.finbox.theme.components.Preference
import dev.achmad.finbox.theme.components.PreferenceScreen
import dev.achmad.finbox.util.locale.currentLanguage
import dev.achmad.finbox.util.locale.displayName
import dev.achmad.finbox.util.locale.setLanguage
import dev.achmad.finbox.util.locale.supportedLanguages
import androidx.compose.ui.res.stringResource
import dev.achmad.finbox.R

/**
 * The languages this build ships, one row each.
 *
 * The list is whatever the build found in the `values-*` folders, so a new
 * translation shows up here on its own.
 */
object SettingsLanguageScreen : Screen {
    private fun readResolve(): Any = SettingsLanguageScreen

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        PreferenceScreen(
            title = stringResource(R.string.language),
            onBackPressed = navigator::pop,
            itemsProvider = { languages() },
        )
    }

    @Composable
    private fun languages(): List<Preference> {
        val context = LocalContext.current
        // Applying a language recreates the activity, but the screen it recreates
        // is this one — so the check has to move now rather than on the next read.
        var selected by remember { mutableStateOf(currentLanguage(context)) }

        return remember(selected) { supportedLanguages(context) }.map { language ->
            Preference.PreferenceItem.CheckPreference(
                value = language,
                checked = language == selected,
                title = displayName(language),
                onClick = { value ->
                    selected = value
                    setLanguage(value)
                },
            )
        }
    }
}