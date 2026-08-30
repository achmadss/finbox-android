package dev.achmad.finbox.di

import dev.achmad.finbox.core.source.SourceManager
import dev.achmad.finbox.core.gmail.GmailApi
import dev.achmad.finbox.core.gmail.GmailApiImpl
import dev.achmad.finbox.core.gmail.GmailAuthManager
import dev.achmad.finbox.core.gmail.GmailAuthManagerImpl
import dev.achmad.finbox.core.gmail.GmailTokenManager
import dev.achmad.finbox.core.gmail.GmailTokenStore
import dev.achmad.finbox.core.update.transaction.TransactionUpdateManager
import dev.achmad.finbox.core.update.transaction.TransactionUpdateStatus
import dev.achmad.finbox.core.update.transaction.TransactionUpdater
import dev.achmad.finbox.core.update.app.AppUpdateChecker
import androidx.work.WorkManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val coreModule = module {
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

    single<SourceManager> {
        SourceManager(
            preferences = get(),
            transactionUpdateManager = get(),
        )
    }
    single<AppUpdateChecker> {
        AppUpdateChecker(
            context = androidContext(),
            client = get(),
            preferences = get()
        )
    }
    single<TransactionUpdater> {
        TransactionUpdater(
            sources = { get<SourceManager>().enabledNow() },
            accountRepository = get(),
            accountSourceRepository = get(),
            emailRepository = get(),
            transactionRepository = get(),
            gmailApi = get(),
            rules = get(),
        )
    }

    // One instance per process, like WorkManager itself: the app context is needed to reach the scheduler.
    single<WorkManager> { WorkManager.getInstance(androidContext()) }
    single<TransactionUpdateManager> {
        TransactionUpdateManager(
            workManager = get(),
            preferences = get(),
            onboardingPreference = get(),
            toastHelper = get()
        )
    }
    single<TransactionUpdateStatus> { TransactionUpdateStatus(workManager = get()) }
}
