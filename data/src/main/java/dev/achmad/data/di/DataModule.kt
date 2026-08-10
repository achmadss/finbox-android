package dev.achmad.data.di

import android.content.Context
import dev.achmad.data.db.DatabaseFactory
import dev.achmad.data.db.FinboxDatabase
import dev.achmad.data.repository.AccountExtensionRepositoryImpl
import dev.achmad.data.repository.AccountRepositoryImpl
import dev.achmad.data.repository.InstalledExtensionRepositoryImpl
import dev.achmad.data.repository.SyncStateRepositoryImpl
import dev.achmad.data.repository.TransactionRepositoryImpl
import dev.achmad.data.repository.UnrecognizedEmailRepositoryImpl
import dev.achmad.domain.repository.AccountExtensionRepository
import dev.achmad.domain.repository.AccountRepository
import dev.achmad.domain.repository.InstalledExtensionRepository
import dev.achmad.domain.repository.SyncStateRepository
import dev.achmad.domain.repository.TransactionRepository
import dev.achmad.domain.repository.UnrecognizedEmailRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataModule = module {

    single { FinboxDatabase(DatabaseFactory.createDriver(androidContext())) }

    single<AccountRepository> { AccountRepositoryImpl(get()) }
    single<AccountExtensionRepository> { AccountExtensionRepositoryImpl(get()) }
    single<InstalledExtensionRepository> { InstalledExtensionRepositoryImpl(get()) }
    single<TransactionRepository> { TransactionRepositoryImpl(get()) }
    single<UnrecognizedEmailRepository> { UnrecognizedEmailRepositoryImpl(get()) }
    single<SyncStateRepository> { SyncStateRepositoryImpl(get()) }

}
