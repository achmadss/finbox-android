package dev.achmad.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.achmad.data.db.Account_parser
import dev.achmad.data.db.FinboxDatabase
import dev.achmad.data.model.AccountParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AccountParserRepository(
    private val db: FinboxDatabase,
) {

    fun forAccount(accountId: String): Flow<List<AccountParser>> =
        db.accountParserQueries.SELECTForAccount(accountId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toModel() } }

    fun allAssignments(): Flow<List<AccountParser>> =
        db.accountParserQueries.SELECTAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toModel() } }

    suspend fun all(): List<AccountParser> = withContext(Dispatchers.IO) {
        db.accountParserQueries.SELECTAll().executeAsList().map { it.toModel() }
    }

    /** Restore path: replaces everything. */
    suspend fun replaceAll(assignments: List<AccountParser>) = withContext(Dispatchers.IO) {
        db.transaction {
            db.accountParserQueries.DELETEAllAssignments()
            for (assignment in assignments) {
                db.accountParserQueries.INSERTOrReplace(
                    accountId = assignment.accountId,
                    sourceId = assignment.sourceId,
                    enabled = if (assignment.enabled) 1L else 0L,
                    position = assignment.position.toLong(),
                )
            }
        }
    }

    suspend fun deleteForAccount(accountId: String) = withContext(Dispatchers.IO) {
        db.accountParserQueries.DELETEForAccount(accountId)
        Unit
    }

    suspend fun setEnabled(accountId: String, sourceId: Long, enabled: Boolean) =
        withContext(Dispatchers.IO) {
            val existing = db.accountParserQueries
                .SELECTForAccountAndSource(accountId, sourceId)
                .executeAsOneOrNull()
            if (existing != null) {
                db.accountParserQueries.SETEnabled(if (enabled) 1L else 0L, accountId, sourceId)
            } else {
                db.accountParserQueries.INSERTOrReplace(
                    accountId = accountId,
                    sourceId = sourceId,
                    enabled = if (enabled) 1L else 0L,
                    position = 0L,
                )
            }
            Unit
        }

    suspend fun reorder(accountId: String, sourceIds: List<Long>) =
        withContext(Dispatchers.IO) {
            db.accountParserQueries.DELETEForAccount(accountId)
            sourceIds.forEachIndexed { index, sourceId ->
                db.accountParserQueries.INSERTOrReplace(
                    accountId = accountId,
                    sourceId = sourceId,
                    enabled = 1L,
                    position = index.toLong(),
                )
            }
        }

    private fun Account_parser.toModel() = AccountParser(
        accountId = account_id,
        sourceId = source_id,
        enabled = enabled != 0L,
        position = position.toInt(),
    )
}
