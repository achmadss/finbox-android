package dev.achmad.finbox.di

import dev.achmad.domain.repository.AccountExtensionRepository
import dev.achmad.domain.repository.AccountRepository
import dev.achmad.domain.repository.InstalledExtensionRepository
import dev.achmad.domain.repository.SyncStateRepository
import dev.achmad.domain.repository.TransactionRepository
import dev.achmad.domain.repository.UnrecognizedEmailRepository
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
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {

    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single { GmailTokenStore(androidContext()) }
    single { GmailTokenManager(get(), get()) }
    single { GmailApi(get(), get()) }
    single { GmailAuthManager(androidContext(), get(), get(), get()) }

    single { ExtensionLoader(androidContext()) }
    single { ExtensionIndex(get()) }
    single { ExtensionInstaller(androidContext(), get(), get()) }
    single {
        ExtensionManager(
            loader = get(),
            installer = get(),
            index = get(),
            repository = get<InstalledExtensionRepository>(),
            scope = get(),
        )
    }

    single { SyncEngine(get(), get(), get(), get(), get(), get()) }
}
