package dev.achmad.finbox.di

import android.content.Context
import dev.achmad.finbox.BuildConfig
import dev.achmad.finbox.core.network.NetworkHelper
import dev.achmad.finbox.core.preference.AndroidPreferenceStore
import dev.achmad.finbox.core.preference.PreferenceStore
import dev.achmad.finbox.core.util.ToastHelper
import dev.achmad.finbox.extension.ExtensionIndex
import dev.achmad.finbox.extension.ExtensionInstaller
import dev.achmad.finbox.extension.ExtensionLoader
import dev.achmad.finbox.extension.ExtensionManager
import dev.achmad.finbox.gmail.GmailApi
import dev.achmad.finbox.gmail.GmailAuthManager
import dev.achmad.finbox.gmail.GmailTokenManager
import dev.achmad.finbox.gmail.GmailTokenStore
import dev.achmad.finbox.sync.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        GmailApi(
            client = get(),
            tokens = get()
        )
    }
    single<GmailAuthManager> {
        GmailAuthManager(
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
    single<ExtensionManager> {
        ExtensionManager(
            loader = get(),
            installer = get(),
            index = get(),
            repository = get()
        )
    }
    single<SyncEngine> {
        SyncEngine(
            extensionManager = get(),
            accountRepository = get(),
            accountExtensionRepository = get(),
            transactionRepository = get(),
            gmailApi = get()
        )
    }
}
