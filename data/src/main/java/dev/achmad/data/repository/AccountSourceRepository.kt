package dev.achmad.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.achmad.data.db.Account_source
import dev.achmad.data.db.FinboxDatabase
import dev.achmad.data.model.AccountSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AccountSourceRepository(
    private val db: FinboxDatabase,
) {

    fun forAccount(accountId: String): Flow<List<AccountSource>> =
        db.accountSourceQueries.SELECTForAccount(accountId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toModel() } }

    fun allAssignments(): Flow<List<AccountSource>> =
        db.accountSourceQueries.SELECTAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toModel() } }

    suspend fun all(): List<AccountSource> = withContext(Dispatchers.IO) {
        db.accountSourceQueries.SELECTAll().executeAsList().map { it.toModel() }
    }

    /** Restore path: replaces everything. */
    suspend fun replaceAll(assignments: List<AccountSource>) = withContext(Dispatchers.IO) {
        db.transaction {
            db.accountSourceQueries.DELETEAllAssignments()
            for (assignment in assignments) {
                db.accountSourceQueries.INSERTOrReplace(
                    accountId = assignment.accountId,
                    sourceId = assignment.sourceId,
                    enabled = if (assignment.enabled) 1L else 0L,
                    position = assignment.position.toLong(),
                )
            }
        }
    }

    suspend fun deleteForAccount(accountId: String) = withContext(Dispatchers.IO) {
        db.accountSourceQueries.DELETEForAccount(accountId)
        Unit
    }

    suspend fun setEnabled(accountId: String, sourceId: String, enabled: Boolean) =
        withContext(Dispatchers.IO) {
            val existing = db.accountSourceQueries
                .SELECTForAccountAndSource(accountId, sourceId)
                .executeAsOneOrNull()
            if (existing != null) {
                db.accountSourceQueries.SETEnabled(if (enabled) 1L else 0L, accountId, sourceId)
            } else {
                db.accountSourceQueries.INSERTOrReplace(
                    accountId = accountId,
                    sourceId = sourceId,
                    enabled = if (enabled) 1L else 0L,
                    position = 0L,
                )
            }
            Unit
        }

    suspend fun reorder(accountId: String, sourceIds: List<String>) =
        withContext(Dispatchers.IO) {
            db.accountSourceQueries.DELETEForAccount(accountId)
            sourceIds.forEachIndexed { index, sourceId ->
                db.accountSourceQueries.INSERTOrReplace(
                    accountId = accountId,
                    sourceId = sourceId,
                    enabled = 1L,
                    position = index.toLong(),
                )
            }
        }

    private fun Account_source.toModel() = AccountSource(
        accountId = account_id,
        sourceId = source_id,
        enabled = enabled != 0L,
        position = position.toInt(),
    )
}
