package dev.achmad.data.di

import dev.achmad.data.db.DatabaseFactory
import dev.achmad.data.db.FinboxDatabase
import dev.achmad.data.repository.AccountExtensionRepository
import dev.achmad.data.repository.AccountRepository
import dev.achmad.data.repository.InstalledExtensionRepository
import dev.achmad.data.repository.TransactionRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataModule = module {

    single { FinboxDatabase(DatabaseFactory.createDriver(androidContext())) }

    single { AccountRepository(get()) }
    single { AccountExtensionRepository(get()) }
    single { InstalledExtensionRepository(get()) }
    single { TransactionRepository(get()) }
}
