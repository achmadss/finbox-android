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
import dev.achmad.finbox.features.settings.language.SettingsLanguageScreen
import dev.achmad.finbox.theme.components.Preference
import dev.achmad.finbox.theme.components.PreferenceScreen
import dev.achmad.finbox.util.formatter.formatDate
import dev.achmad.finbox.util.koin.inject
import dev.achmad.finbox.util.locale.currentLanguage
import dev.achmad.finbox.util.locale.displayName
import dev.achmad.finbox.util.preference.collectAsState
import dev.achmad.finbox.util.ui.rememberUse24HourClock
import androidx.compose.ui.res.stringResource
import dev.achmad.finbox.R
import dev.achmad.finbox.core.llm.LlmProviderStore
import dev.achmad.finbox.features.settings.categorize.SettingsCategorizeScreen
import dev.achmad.finbox.features.settings.llm.SettingsLlmScreen

/** Every setting the app has, in one screen. */
object SettingsScreen : Screen {
    private fun readResolve(): Any = SettingsScreen

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        PreferenceScreen(
            title = stringResource(R.string.label_settings),
            onBackPressed = navigator::pop,
            itemsProvider = {
                listOf(
                    appearanceGroup(),
                    syncGroup(),
                    categorizationGroup(),
                    dataGroup(),
                    systemGroup(),
                )
            },
        )
    }

    /**
     * The optional AI. Its own group rather than a line under Sync, because
     * setting it up is a decision — it sends signatures to somebody else's
     * server — and nothing else in the app needs it.
     */
    @Composable
    private fun categorizationGroup(): Preference.PreferenceGroup {
        val navigator = LocalNavigator.currentOrThrow
        val providers = remember { inject<LlmProviderStore>() }
        val active = remember { providers.active() }

        return Preference.PreferenceGroup(
            title = stringResource(R.string.pref_category_categorization),
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_llm_providers),
                    subtitle = active
                        ?.let { stringResource(R.string.pref_llm_providers_summ, it.name, it.model) }
                        ?: stringResource(R.string.pref_llm_providers_summ_none),
                    onClick = { navigator.push(SettingsLlmScreen) },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_auto_categorize),
                    subtitle = if (active != null) {
                        stringResource(R.string.pref_auto_categorize_summ)
                    } else {
                        stringResource(R.string.pref_auto_categorize_summ_off)
                    },
                    onClick = { navigator.push(SettingsCategorizeScreen) },
                ),
            ),
        )
    }

    @Composable
    private fun appearanceGroup(): Preference.PreferenceGroup {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val preferences = remember { inject<UiPreferences>() }
        val themeMode by preferences.themeMode().collectAsState()

        return Preference.PreferenceGroup(
            title = stringResource(R.string.pref_category_appearance),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = preferences.themeMode(),
                    title = stringResource(R.string.pref_app_theme),
                    entries = mapOf(
                        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                        ThemeMode.LIGHT to stringResource(R.string.theme_light),
                        ThemeMode.DARK to stringResource(R.string.theme_dark),
                    ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = preferences.amoledDark(),
                    title = stringResource(R.string.pref_dark_theme_pure_black),
                    // Nothing to see while the app is held in light mode.
                    enabled = themeMode != ThemeMode.LIGHT,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = preferences.dynamicColor(),
                    title = stringResource(R.string.pref_dynamic_color),
                    subtitle = stringResource(R.string.pref_dynamic_color_summ),
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = preferences.use24HourClock(),
                    title = stringResource(R.string.pref_24_hour_clock),
                    subtitle = stringResource(R.string.pref_24_hour_clock_summ),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.language),
                    subtitle = displayName(currentLanguage(context)),
                    onClick = { navigator.push(SettingsLanguageScreen) },
                ),
            ),
        )
    }

    @Composable
    private fun syncGroup(): Preference.PreferenceGroup {
        val model = rememberScreenModel { SettingsScreenModel() }
        val preferences = remember { inject<SyncPreferences>() }
        val intervalHours by preferences.autoFetchIntervalHours().collectAsState()
        val lastSync by model.lastSync.collectAsState()
        val use24Hour = rememberUse24HourClock()

        val reschedule: suspend (Any?) -> Unit = { model.rescheduleFetch() }

        return Preference.PreferenceGroup(
            title = stringResource(R.string.sync),
            preferenceItems = listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = preferences.autoFetchIntervalHours(),
                    title = stringResource(R.string.pref_fetch_interval),
                    entries = intervalEntries(),
                    onValueChanged = reschedule,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = preferences.fetchOnUnmeteredOnly(),
                    title = stringResource(R.string.pref_fetch_unmetered),
                    subtitle = stringResource(R.string.pref_fetch_unmetered_summ),
                    enabled = intervalHours > 0,
                    onValueChanged = reschedule,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = preferences.fetchWhenChargingOnly(),
                    title = stringResource(R.string.pref_fetch_charging),
                    enabled = intervalHours > 0,
                    onValueChanged = reschedule,
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = preferences.fetchWhenBatteryNotLow(),
                    title = stringResource(R.string.pref_fetch_battery_not_low),
                    enabled = intervalHours > 0,
                    onValueChanged = reschedule,
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_sync_now),
                    subtitle = lastSync
                        ?.let { stringResource(R.string.last_synced, formatDate(it, use24Hour)) }
                        ?: stringResource(R.string.never_synced),
                    onClick = { model.fetchNow() },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_reindex),
                    subtitle = stringResource(R.string.pref_reindex_summ),
                    onClick = { model.reindexTransactions() },
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
            title = stringResource(R.string.pref_category_data),
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_create_backup),
                    subtitle = stringResource(R.string.pref_create_backup_summ),
                    enabled = !busy,
                    onClick = { createBackup.launch(model.backupFileName()) },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_restore_backup),
                    subtitle = stringResource(R.string.pref_restore_backup_summ),
                    enabled = !busy,
                    onClick = { restoreBackup.launch(arrayOf("*/*")) },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_export_csv),
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
            title = stringResource(R.string.pref_category_system),
            preferenceItems = listOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_manage_notifications),
                    // The system owns this screen, and its version is always current.
                    onClick = {
                        context.openSystemSettings(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = preferences.checkParserUpdates(),
                    title = stringResource(R.string.parser_updates),
                    subtitle = stringResource(R.string.pref_check_updates_summ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = preferences.checkAppUpdates(),
                    title = stringResource(R.string.app_updates),
                    subtitle = stringResource(R.string.pref_check_updates_summ),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(R.string.pref_check_app_updates_now),
                    onClick = { model.checkAppUpdateNow() },
                ),
            ),
        )
    }

    /** 0 turns the schedule off; the rest are hours between syncs. */
    @Composable
    private fun intervalEntries(): Map<Int, String> = mapOf(
        0 to stringResource(R.string.fetch_interval_never),
        1 to stringResource(R.string.fetch_interval_hourly),
        2 to stringResource(R.string.fetch_interval_hours, 2),
        3 to stringResource(R.string.fetch_interval_hours, 3),
        6 to stringResource(R.string.fetch_interval_hours, 6),
        12 to stringResource(R.string.fetch_interval_hours, 12),
        24 to stringResource(R.string.fetch_interval_daily),
        48 to stringResource(R.string.fetch_interval_days, 2),
    )
}
