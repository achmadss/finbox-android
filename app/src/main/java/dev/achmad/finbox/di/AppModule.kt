package dev.achmad.finbox.di

import android.content.Context
import dev.achmad.finbox.BuildConfig
import dev.achmad.finbox.util.network.NetworkHelper
import dev.achmad.finbox.util.preference.AndroidPreferenceStore
import dev.achmad.finbox.util.preference.PreferenceStore
import dev.achmad.finbox.util.ui.ToastHelper
import dev.achmad.finbox.core.parser.ParserIndex
import dev.achmad.finbox.core.parser.ParserInstaller
import dev.achmad.finbox.core.preference.ParserKindPreference
import dev.achmad.finbox.core.parser.ParserLoader
import dev.achmad.finbox.core.parser.ParserManager
import dev.achmad.finbox.core.parser.ParserUpdateChecker
import dev.achmad.finbox.core.gmail.GmailApi
import dev.achmad.finbox.core.gmail.GmailApiImpl
import dev.achmad.finbox.core.gmail.GmailAuthManager
import dev.achmad.finbox.core.gmail.GmailAuthManagerImpl
import dev.achmad.finbox.core.gmail.GmailTokenManager
import dev.achmad.finbox.core.gmail.GmailTokenStore
import dev.achmad.finbox.core.statement.StatementUpdater
import dev.achmad.finbox.core.preference.OnboardingPreference
import dev.achmad.finbox.core.preference.SyncPreferences
import dev.achmad.finbox.core.preference.UpdatePreferences
import dev.achmad.finbox.core.update.AppUpdateChecker
import dev.achmad.finbox.core.preference.UiPreferences
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
    single<ParserLoader> { ParserLoader(androidContext()) }
    single<ParserIndex> { ParserIndex(client = get()) }
    single<ParserInstaller> {
        ParserInstaller(
            client = get(),
            loader = get()
        )
    }
    single<ParserKindPreference> { ParserKindPreference(preferenceStore = get()) }
    single<ParserManager> {
        ParserManager(
            context = androidContext(),
            loader = get(),
            installer = get(),
            index = get(),
            repository = get(),
            kindPreference = get()
        )
    }
    single<ParserUpdateChecker> {
        ParserUpdateChecker(
            context = androidContext(),
            manager = get(),
            updatePreferences = get(),
            preferenceStore = get()
        )
    }
    single<AppUpdateChecker> {
        AppUpdateChecker(
            context = androidContext(),
            client = get(),
            preferences = get()
        )
    }
    single<OnboardingPreference> { OnboardingPreference(preferenceStore = get()) }
    single<UiPreferences> { UiPreferences(preferenceStore = get()) }
    single<SyncPreferences> { SyncPreferences(preferenceStore = get()) }
    single<UpdatePreferences> { UpdatePreferences(preferenceStore = get()) }
    single<StatementUpdater> {
        StatementUpdater(
            sources = { get<ParserManager>().sources },
            accountRepository = get(),
            accountParserRepository = get(),
            emailRepository = get(),
            transactionRepository = get(),
            gmailApi = get(),
            kindPreference = get()
        )
    }
}
