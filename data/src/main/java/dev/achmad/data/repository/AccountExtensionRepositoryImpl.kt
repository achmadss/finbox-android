package dev.achmad.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.achmad.data.db.Account_extension
import dev.achmad.data.db.FinboxDatabase
import dev.achmad.domain.model.AccountExtension
import dev.achmad.domain.repository.AccountExtensionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AccountExtensionRepositoryImpl(
    private val db: FinboxDatabase,
) : AccountExtensionRepository {

    override fun forAccount(accountId: String): Flow<List<AccountExtension>> =
        db.accountExtensionQueries.SELECTForAccount(accountId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toModel() } }

    override fun allAssignments(): Flow<List<AccountExtension>> =
        db.accountExtensionQueries.SELECTAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toModel() } }

    override suspend fun deleteForAccount(accountId: String) = withContext(Dispatchers.IO) {
        db.accountExtensionQueries.DELETEForAccount(accountId)
    }

    override suspend fun setEnabled(accountId: String, sourceId: Long, enabled: Boolean) =
        withContext(Dispatchers.IO) {
            val existing = db.accountExtensionQueries
                .SELECTForAccountAndSource(accountId, sourceId)
                .executeAsOneOrNull()
            if (existing != null) {
                db.accountExtensionQueries.SETEnabled(if (enabled) 1L else 0L, accountId, sourceId)
            } else {
                db.accountExtensionQueries.INSERTOrReplace(
                    accountId = accountId,
                    sourceId = sourceId,
                    enabled = if (enabled) 1L else 0L,
                    position = 0L,
                )
            }
        }

    override suspend fun reorder(accountId: String, sourceIds: List<Long>) =
        withContext(Dispatchers.IO) {
            db.accountExtensionQueries.DELETEForAccount(accountId)
            sourceIds.forEachIndexed { index, sourceId ->
                db.accountExtensionQueries.INSERTOrReplace(
                    accountId = accountId,
                    sourceId = sourceId,
                    enabled = 1L,
                    position = index.toLong(),
                )
            }
        }

    private fun Account_extension.toModel() = AccountExtension(
        accountId = account_id,
        sourceId = source_id,
        enabled = enabled != 0L,
        position = position.toInt(),
    )
}
