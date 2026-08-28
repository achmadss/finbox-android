package dev.achmad.finbox.di

import dev.achmad.finbox.core.parser.ParserIndex
import dev.achmad.finbox.core.parser.ParserInstaller
import dev.achmad.finbox.core.preference.ParserMethodPreference
import dev.achmad.finbox.core.parser.ParserLoader
import dev.achmad.finbox.core.parser.ParserManager
import dev.achmad.finbox.core.parser.ParserUpdateChecker
import dev.achmad.finbox.core.parser.ParserUpdateNotifier
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
import dev.achmad.finbox.core.categorization.CategorizationManager
import dev.achmad.finbox.core.categorization.TransactionCategorizer
import dev.achmad.finbox.core.llm.LlmClient
import dev.achmad.finbox.core.llm.TransactionClassifier
import dev.achmad.finbox.core.llm.LlmKeyStore
import dev.achmad.finbox.core.llm.LlmProviderStore
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
    single<LlmKeyStore> { LlmKeyStore(context = androidContext()) }
    single<LlmProviderStore> { LlmProviderStore(preferenceStore = get(), keys = get()) }
    single<LlmClient> { LlmClient(client = get(), providers = get()) }
    single<TransactionClassifier> { TransactionClassifier(client = get(), providers = get()) }
    single<TransactionCategorizer> {
        TransactionCategorizer(
            transactions = get(),
            runs = get(),
            classifier = get(),
            providers = get(),
        )
    }
    single<CategorizationManager> { CategorizationManager(categorizer = get(), runs = get()) }

    single<ParserLoader> { ParserLoader(androidContext()) }
    single<ParserIndex> { ParserIndex(client = get()) }
    single<ParserInstaller> {
        ParserInstaller(
            client = get(),
            loader = get()
        )
    }
    single<ParserMethodPreference> { ParserMethodPreference(preferenceStore = get()) }
    single<ParserManager> {
        ParserManager(
            transactionUpdateManager = get(),
            loader = get(),
            installer = get(),
            index = get(),
            repository = get(),
            methodPreference = get()
        )
    }
    single<ParserUpdateNotifier> { ParserUpdateNotifier(context = androidContext()) }
    single<ParserUpdateChecker> {
        ParserUpdateChecker(
            notifier = get(),
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
    single<TransactionUpdater> {
        TransactionUpdater(
            parsers = { get<ParserManager>().parsers },
            accountRepository = get(),
            accountParserRepository = get(),
            emailRepository = get(),
            transactionRepository = get(),
            gmailApi = get(),
            methodPreference = get()
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
