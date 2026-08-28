package dev.achmad.finbox.theme.components

import androidx.compose.ui.graphics.vector.ImageVector
import dev.achmad.finbox.util.preference.Preference as PreferenceData

/**
 * A settings screen described as data, in the shape Tachiyomi's settings use.
 * Items that own a value take the [PreferenceData] directly, so the screen
 * holds no state of its own and reads values changed elsewhere.
 */
sealed class Preference {

    abstract val title: String
    abstract val enabled: Boolean

    sealed class PreferenceItem : Preference() {

        abstract val subtitle: String?
        abstract val icon: ImageVector?

        /** A row that does something when tapped, or just says something when [onClick] is null. */
        data class TextPreference(
            override val title: String,
            override val subtitle: String? = null,
            override val icon: ImageVector? = null,
            override val enabled: Boolean = true,
            val onClick: (() -> Unit)? = null,
        ) : PreferenceItem()

        /** A row with a switch, wired straight to [preference]. */
        data class SwitchPreference(
            val preference: PreferenceData<Boolean>,
            override val title: String,
            override val subtitle: String? = null,
            override val icon: ImageVector? = null,
            override val enabled: Boolean = true,
            /** Runs after the value is stored — rescheduling a job, say. */
            val onValueChanged: suspend (Boolean) -> Unit = {},
        ) : PreferenceItem()

        /** A row that opens a dialog to pick one of [entries]. */
        @Suppress("UNCHECKED_CAST")
        data class ListPreference<T>(
            val preference: PreferenceData<T>,
            val entries: Map<T, String>,
            override val title: String,
            /** `%s` stands for the selected entry. */
            override val subtitle: String? = "%s",
            override val icon: ImageVector? = null,
            override val enabled: Boolean = true,
            val onValueChanged: suspend (T) -> Unit = {},
        ) : PreferenceItem() {
            // The renderer only ever sees ListPreference<*>, so the cast to T happens here.
            internal fun internalSet(value: Any?) = preference.set(value as T)
            internal suspend fun internalOnValueChanged(value: Any?) = onValueChanged(value as T)
            internal fun internalEntryOf(value: Any?) = entries[value as T]
        }

        /** A checked row for choices spread over a screen rather than folded into a dialog. */
        data class CheckPreference(
            val value: String,
            val checked: Boolean,
            override val title: String,
            override val subtitle: String? = null,
            override val icon: ImageVector? = null,
            override val enabled: Boolean = true,
            val onClick: (String) -> Unit,
        ) : PreferenceItem()

        /** A paragraph of explanation rather than a row: no tap target, no value. */
        data class InfoPreference(
            override val title: String,
        ) : PreferenceItem() {
            override val subtitle: String? = null
            override val icon: ImageVector? = null
            override val enabled: Boolean = true
        }
    }

    data class PreferenceGroup(
        override val title: String,
        override val enabled: Boolean = true,
        val preferenceItems: List<PreferenceItem>,
    ) : Preference()
}
