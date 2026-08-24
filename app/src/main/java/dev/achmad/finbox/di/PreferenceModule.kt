package dev.achmad.finbox.di

import android.content.Context
import dev.achmad.finbox.core.preference.OnboardingPreference
import dev.achmad.finbox.core.preference.SyncPreferences
import dev.achmad.finbox.core.preference.UiPreferences
import dev.achmad.finbox.core.preference.UpdatePreferences
import dev.achmad.finbox.util.preference.AndroidPreferenceStore
import dev.achmad.finbox.util.preference.PreferenceStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val preferenceModule = module {
    single<PreferenceStore> {
        AndroidPreferenceStore(
            sharedPreferences = androidContext()
                .getSharedPreferences(
                    "app_pref",
                    Context.MODE_PRIVATE
                ),
        )
    }
    single<OnboardingPreference> { OnboardingPreference(preferenceStore = get()) }
    single<UiPreferences> { UiPreferences(preferenceStore = get()) }
    single<SyncPreferences> { SyncPreferences(preferenceStore = get()) }
    single<UpdatePreferences> { UpdatePreferences(preferenceStore = get()) }
}