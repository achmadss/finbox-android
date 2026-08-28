package dev.achmad.data.di

import dev.achmad.data.backup.BackupManager
import dev.achmad.data.db.DatabaseFactory
import dev.achmad.data.db.FinboxDatabase
import dev.achmad.data.export.CsvExport
import dev.achmad.data.repository.AccountExtensionRepository
import dev.achmad.data.repository.AccountRepository
import dev.achmad.data.repository.EmailRepository
import dev.achmad.data.repository.ClassificationRunRepository
import dev.achmad.data.repository.TransactionRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataModule = module {

    single { FinboxDatabase(DatabaseFactory.createDriver(androidContext())) }

    single { AccountRepository(get()) }
    single { AccountExtensionRepository(get()) }
    single { EmailRepository(get()) }
    single { TransactionRepository(get()) }
    single { ClassificationRunRepository(get()) }

    /** Whole-app backup and restore, `.finboxbackup`. */
    single { BackupManager(androidContext(), get(), get(), get(), get()) }

    /** The ledger as a spreadsheet. Export only. */
    single { CsvExport(androidContext(), get()) }
}
