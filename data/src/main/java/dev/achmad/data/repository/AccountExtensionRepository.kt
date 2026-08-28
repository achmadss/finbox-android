package dev.achmad.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.achmad.data.db.Account_extension
import dev.achmad.data.db.FinboxDatabase
import dev.achmad.data.model.AccountExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AccountExtensionRepository(
    private val db: FinboxDatabase,
) {

    fun forAccount(accountId: String): Flow<List<AccountExtension>> =
        db.accountExtensionQueries.SELECTForAccount(accountId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toModel() } }

    fun allAssignments(): Flow<List<AccountExtension>> =
        db.accountExtensionQueries.SELECTAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toModel() } }

    suspend fun all(): List<AccountExtension> = withContext(Dispatchers.IO) {
        db.accountExtensionQueries.SELECTAll().executeAsList().map { it.toModel() }
    }

    /** Restore path: replaces everything. */
    suspend fun replaceAll(assignments: List<AccountExtension>) = withContext(Dispatchers.IO) {
        db.transaction {
            db.accountExtensionQueries.DELETEAllAssignments()
            for (assignment in assignments) {
                db.accountExtensionQueries.INSERTOrReplace(
                    accountId = assignment.accountId,
                    extensionId = assignment.extensionId,
                    enabled = if (assignment.enabled) 1L else 0L,
                    position = assignment.position.toLong(),
                )
            }
        }
    }

    suspend fun deleteForAccount(accountId: String) = withContext(Dispatchers.IO) {
        db.accountExtensionQueries.DELETEForAccount(accountId)
        Unit
    }

    suspend fun setEnabled(accountId: String, extensionId: String, enabled: Boolean) =
        withContext(Dispatchers.IO) {
            val existing = db.accountExtensionQueries
                .SELECTForAccountAndExtension(accountId, extensionId)
                .executeAsOneOrNull()
            if (existing != null) {
                db.accountExtensionQueries.SETEnabled(if (enabled) 1L else 0L, accountId, extensionId)
            } else {
                db.accountExtensionQueries.INSERTOrReplace(
                    accountId = accountId,
                    extensionId = extensionId,
                    enabled = if (enabled) 1L else 0L,
                    position = 0L,
                )
            }
            Unit
        }

    suspend fun reorder(accountId: String, extensionIds: List<String>) =
        withContext(Dispatchers.IO) {
            db.accountExtensionQueries.DELETEForAccount(accountId)
            extensionIds.forEachIndexed { index, extensionId ->
                db.accountExtensionQueries.INSERTOrReplace(
                    accountId = accountId,
                    extensionId = extensionId,
                    enabled = 1L,
                    position = index.toLong(),
                )
            }
        }

    private fun Account_extension.toModel() = AccountExtension(
        accountId = account_id,
        extensionId = extension_id,
        enabled = enabled != 0L,
        position = position.toInt(),
    )
}
