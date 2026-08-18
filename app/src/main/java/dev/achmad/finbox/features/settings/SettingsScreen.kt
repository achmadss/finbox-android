package dev.achmad.finbox.features.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.achmad.finbox.core.preference.SyncPreferences
import dev.achmad.finbox.core.preference.ThemeMode
import dev.achmad.finbox.core.preference.UiPreferences
import dev.achmad.finbox.core.preference.UpdatePreferences
import dev.achmad.finbox.core.statement.StatementUpdateJob
import dev.achmad.finbox.theme.components.Preference
import dev.achmad.finbox.theme.components.PreferenceScreen
import dev.achmad.finbox.util.formatter.formatDate
import dev.achmad.finbox.util.koin.inject
import dev.achmad.finbox.util.locale.currentLanguage
import dev.achmad.finbox.util.locale.displayName
import dev.achmad.finbox.util.preference.collectAsState
import dev.achmad.finbox.util.ui.rememberUse24HourClock

/** Every setting the app has, in one screen. */
object SettingsScreen : Screen {
    private fun readResolve(): Any = SettingsScreen

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        PreferenceScreen(
            title = "Settings",
            onBackPressed = navigator::pop,
            itemsProvider = {
                listOf(
                    appearanceGroup(),
                    syncGroup(),
                    dataGroup(),
                    systemGroup(),
                )
            },
        )
    }

    @Composable
    private fun appearanceGroup(): Preference.PreferenceGroup {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val preferences = remember { inject<UiPreferences>() }
        val themeMode by preferences.themeMode().collectAsState()

        return Preference.PreferenceGroup(
            title = "Appearance",
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = preferences.themeMode(),
                    title = "Theme",
                    entries = mapOf(
                        ThemeMode.SYSTEM to "Follow system",
                        ThemeMode.LIGHT to "Light",
                        ThemeMode.DARK to "Dark",
                    ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = preferences.amoledDark(),
                    title = "Pure black mode",
                    // Nothing to see while the app is held in light mode.
                    enabled = themeMode != ThemeMode.LIGHT,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = preferences.dynamicColor(),
                    title = "Dynamic colors",
                    subtitle = "Match the app colors to your wallpaper",
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = preferences.use24HourClock(),
                    title = "24-hour clock",
                    subtitle = "Show 14:05 instead of 2:05 PM",
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Language",
                    subtitle = displayName(currentLanguage(context)),
                    onClick = { navigator.push(SettingsLanguageScreen) },
                ),
            ),
        )
    }

    @Composable
    private fun syncGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val model = rememberScreenModel { SettingsScreenModel() }
        val preferences = remember { inject<SyncPreferences>() }
        val intervalHours by preferences.autoFetchIntervalHours().collectAsState()
        val lastSync by model.lastSync.collectAsState()
        val use24Hour = rememberUse24HourClock()

        // WorkManager replaces the job in place, so a new schedule or condition
        // applies now instead of after the old period runs out.
        val reschedule: suspend (Any?) -> Unit = { StatementUpdateJob.schedule(context) }

        return Preference.PreferenceGroup(
            title = "Sync",
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = preferences.autoFetchIntervalHours(),
                    title = "Check for new email",
                    entries = INTERVAL_ENTRIES,
                    onValueChanged = reschedule,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = preferences.fetchOnUnmeteredOnly(),
                    title = "Wi-Fi only",
                    subtitle = "Never sync on mobile data",
                    enabled = intervalHours > 0,
                    onValueChanged = reschedule,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = preferences.fetchWhenChargingOnly(),
                    title = "While charging only",
                    enabled = intervalHours > 0,
                    onValueChanged = reschedule,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = preferences.fetchWhenBatteryNotLow(),
                    title = "Skip on low battery",
                    enabled = intervalHours > 0,
                    onValueChanged = reschedule,
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Sync now",
                    subtitle = lastSync?.let { "Last synced ${formatDate(it, use24Hour)}" }
                        ?: "Never synced",
                    onClick = { model.fetchNow(context) },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Reindex transactions",
                    subtitle = "Force app to recheck your cached emails for new transactions",
                    onClick = { model.reindexTransactions(context) },
                ),
            ),
        )
    }

    @Composable
    private fun dataGroup(): Preference.PreferenceGroup {
        val model = rememberScreenModel { SettingsScreenModel() }
        val busy by model.busy.collectAsState()

        val createBackup = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/gzip"),
        ) { uri -> uri?.let(model::createBackup) }
        val restoreBackup = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let(model::restoreBackup) }
        val exportCsv = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("text/csv"),
        ) { uri -> uri?.let(model::exportCsv) }

        return Preference.PreferenceGroup(
            title = "Data and storage",
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = "Create backup",
                    subtitle = "Save your accounts, email and transactions to a file",
                    enabled = !busy,
                    onClick = { createBackup.launch(model.backupFileName()) },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Restore backup",
                    subtitle = "Replace everything here with a backup file",
                    enabled = !busy,
                    onClick = { restoreBackup.launch(arrayOf("*/*")) },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Export as CSV",
                    enabled = !busy,
                    onClick = { exportCsv.launch(model.csvFileName()) },
                ),
            ),
        )
    }

    @Composable
    private fun systemGroup(): Preference.PreferenceGroup {
        val context = LocalContext.current
        val model = rememberScreenModel { SettingsScreenModel() }
        val preferences = remember { inject<UpdatePreferences>() }

        return Preference.PreferenceGroup(
            title = "System",
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = "Manage notifications",
                    // The system owns this screen, and its version is always current.
                    onClick = {
                        context.openSystemSettings(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = preferences.checkExtensionUpdates(),
                    title = "Extension updates",
                    subtitle = "Check once a day when the app opens",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = preferences.checkAppUpdates(),
                    title = "App updates",
                    subtitle = "Check once a day when the app opens",
                ),
                Preference.PreferenceItem.TextPreference(
                    title = "Check for app updates now",
                    onClick = { model.checkAppUpdateNow() },
                ),
            ),
        )
    }

    /** 0 turns the schedule off; the rest are hours between syncs. */
    private val INTERVAL_ENTRIES = mapOf(
        0 to "Never",
        1 to "Every hour",
        2 to "Every 2 hours",
        3 to "Every 3 hours",
        6 to "Every 6 hours",
        12 to "Every 12 hours",
        24 to "Every day",
        48 to "Every 2 days",
    )
}
