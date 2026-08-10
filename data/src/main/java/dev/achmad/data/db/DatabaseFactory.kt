package dev.achmad.data.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

object DatabaseFactory {
    private const val DB_NAME = "finbox.db"

    fun createDriver(context: Context): SqlDriver =
        AndroidSqliteDriver(
            schema = FinboxDatabase.Schema,
            context = context,
            name = DB_NAME,
        )
}
