package com.example.core.di

import android.content.Context
import com.example.core.network.NetworkHelper
import com.example.core.preference.AndroidPreferenceStore
import com.example.core.preference.PreferenceStore
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val coreModule = module {
    single { NetworkHelper(androidContext(), true) }
    single<PreferenceStore> {
        AndroidPreferenceStore(
            androidContext().getSharedPreferences("app_pref", Context.MODE_PRIVATE)
        )
    }
}