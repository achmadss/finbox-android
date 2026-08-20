package dev.achmad.finbox

import android.app.Application
import android.util.Log
import dev.achmad.data.di.dataModule
import dev.achmad.finbox.core.update.transaction.TransactionUpdateJob
import dev.achmad.finbox.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.core.logger.Logger
import org.koin.core.logger.MESSAGE

class MainApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            logger(
                object: Logger() {
                    override fun display(level: Level, msg: MESSAGE) {
                        when (level) {
                            Level.DEBUG -> Log.d(null, msg)
                            Level.INFO -> Log.i(null, msg)
                            Level.WARNING -> Log.w(null, msg)
                            Level.ERROR -> Log.e(null, msg)
                            Level.NONE -> Log.v(null, msg)
                        }
                    }
                }
            )
            androidContext(this@MainApplication)
            modules(dataModule, appModule)
        }

        // The fetch schedule is the app's, not a screen's: it has to exist even if
        // nobody opens settings.
        TransactionUpdateJob.schedule(this)
    }
}