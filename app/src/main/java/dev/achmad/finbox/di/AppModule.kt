package dev.achmad.finbox.di

import android.content.Context
import dev.achmad.finbox.BuildConfig
import dev.achmad.finbox.util.network.NetworkHelper
import dev.achmad.finbox.util.preference.AndroidPreferenceStore
import dev.achmad.finbox.util.preference.PreferenceStore
import dev.achmad.finbox.util.ui.ToastHelper
import dev.achmad.finbox.core.extension.ExtensionIndex
import dev.achmad.finbox.core.extension.ExtensionInstaller
import dev.achmad.finbox.core.extension.ExtensionKindPreference
import dev.achmad.finbox.core.extension.ExtensionLoader
import dev.achmad.finbox.core.extension.ExtensionManager
import dev.achmad.finbox.core.extension.ExtensionUpdateChecker
import dev.achmad.finbox.core.gmail.GmailApi
import dev.achmad.finbox.core.gmail.GmailApiImpl
import dev.achmad.finbox.core.gmail.GmailAuthManager
import dev.achmad.finbox.core.gmail.GmailAuthManagerImpl
import dev.achmad.finbox.core.gmail.GmailTokenManager
import dev.achmad.finbox.core.gmail.GmailTokenStore
import dev.achmad.finbox.core.statement.StatementUpdater
import dev.achmad.finbox.features.onboarding.OnboardingPreference
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single<ToastHelper> { ToastHelper(context = androidContext()) }
    single<OkHttpClient> { get<NetworkHelper>().client }
    single<NetworkHelper> {
        NetworkHelper(
            context = androidContext(),
            isDebugBuild = BuildConfig.DEBUG
        )
    }
    single<PreferenceStore> {
        AndroidPreferenceStore(
            sharedPreferences = androidContext()
                .getSharedPreferences(
                    "app_pref",
                    Context.MODE_PRIVATE
                ),
        )
    }
    single<GmailTokenStore> { GmailTokenStore(context = androidContext()) }
    single<GmailTokenManager> {
        GmailTokenManager(
            store = get(),
            client = get()
        )
    }
    single<GmailApi> {
        GmailApiImpl(
            client = get(),
            tokens = get()
        )
    }
    single<GmailAuthManager> {
        GmailAuthManagerImpl(
            context = androidContext(),
            store = get(),
            tokens = get(),
            accountRepository = get()
        )
    }
    single<ExtensionLoader> { ExtensionLoader(androidContext()) }
    single<ExtensionIndex> { ExtensionIndex(client = get()) }
    single<ExtensionInstaller> {
        ExtensionInstaller(
            client = get(),
            loader = get()
        )
    }
    single<ExtensionKindPreference> { ExtensionKindPreference(preferenceStore = get()) }
    single<ExtensionManager> {
        ExtensionManager(
            loader = get(),
            installer = get(),
            index = get(),
            repository = get(),
            kindPreference = get()
        )
    }
    single<ExtensionUpdateChecker> {
        ExtensionUpdateChecker(
            context = androidContext(),
            manager = get(),
            preferenceStore = get()
        )
    }
    single<OnboardingPreference> { OnboardingPreference(preferenceStore = get()) }
    single<StatementUpdater> {
        StatementUpdater(
            sources = { get<ExtensionManager>().sources },
            accountRepository = get(),
            accountExtensionRepository = get(),
            emailRepository = get(),
            transactionRepository = get(),
            gmailApi = get(),
            kindPreference = get()
        )
    }
}
