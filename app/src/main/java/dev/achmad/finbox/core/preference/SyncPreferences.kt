package dev.achmad.finbox.core.preference

import dev.achmad.finbox.util.preference.PreferenceStore

/**
 * When the app goes looking for new mail on its own.
 *
 * Only the schedule reads these. A refresh the user asked for runs whatever the
 * constraints say, since waiting for Wi-Fi is not what "fetch now" means.
 */
class SyncPreferences(
    private val preferenceStore: PreferenceStore,
) {

    /** Hours between automatic fetches. 0 turns the schedule off. */
    fun autoFetchIntervalHours() = preferenceStore.getInt("auto_fetch_interval_hours", 6)

    fun fetchOnUnmeteredOnly() = preferenceStore.getBoolean("fetch_unmetered_only", false)

    fun fetchWhenChargingOnly() = preferenceStore.getBoolean("fetch_charging_only", false)

    fun fetchWhenBatteryNotLow() = preferenceStore.getBoolean("fetch_battery_not_low", true)
}
