package dev.achmad.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.achmad.data.db.FinboxDatabase
import dev.achmad.data.db.Transactions
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TransactionRepository(
    private val db: FinboxDatabase,
) {

    fun transactions(): Flow<List<Transaction>> =
        db.transactionQueries.SELECTAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toModel() } }

    fun search(query: String): Flow<List<Transaction>> {
        val q = query.trim()
        return if (q.isEmpty()) {
            transactions()
        } else {
            db.transactionQueries.SELECTSearch(q)
                .asFlow()
                .mapToList(Dispatchers.IO)
                .map { rows -> rows.map { it.toModel() } }
        }
    }

    /** Everything, deleted rows included — the export and backup view. */
    suspend fun all(): List<Transaction> = withContext(Dispatchers.IO) {
        db.transactionQueries.SELECTEverything().executeAsList().map { it.toModel() }
    }

    suspend fun getById(id: String): Transaction? = withContext(Dispatchers.IO) {
        db.transactionQueries.SELECTById(id).executeAsOneOrNull()?.toModel()
    }

    suspend fun threadIds(accountId: String): Set<String> = withContext(Dispatchers.IO) {
        db.transactionQueries.SELECTThreadIds(accountId)
            .executeAsList()
            .filterNotNull()
            .toSet()
    }

    /**
     * Writes parsed transactions, updating parser-owned fields for an existing id.
     *
     * Ids are derived from a provider reference, thread, or email fallback, so
     * duplicate messages keep the first row without resetting user-owned fields;
     * a reparse of the same message may refresh parser-owned fields.
     */
    suspend fun upsertAll(transactions: List<Transaction>) = withContext(Dispatchers.IO) {
        db.transaction {
            transactions.forEach { transaction ->
                val existing = db.transactionQueries.SELECTById(transaction.id).executeAsOneOrNull()
                if (existing == null) {
                    insert(transaction)
                } else if (existing.email_message_id == transaction.emailMessageId) {
                    updateParsed(transaction)
                }
            }
        }
    }

    suspend fun update(transaction: Transaction) = withContext(Dispatchers.IO) {
        db.transactionQueries.UPDATEById(
            date = transaction.date,
            amount = transaction.amount,
            currency = transaction.currency,
            type = transaction.type?.name,
            category = transaction.category,
            description = transaction.description,
            merchant = transaction.merchant,
            updated_at = transaction.updatedAt,
            id = transaction.id,
        )
        Unit
    }

    /**
     * Drops everything a source parsed under one kind — what switching that kind
     * off means, since a re-parse will not write them back.
     */
    suspend fun deleteByKind(sourceIds: Collection<Long>, kind: String) = withContext(Dispatchers.IO) {
        db.transaction {
            sourceIds.forEach { db.transactionQueries.DELETEByKind(it, kind) }
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        db.transactionQueries.DELETEById(System.currentTimeMillis(), id)
        Unit
    }

    /** Restore path: replaces everything. */
    suspend fun replaceAll(transactions: List<Transaction>) = withContext(Dispatchers.IO) {
        db.transaction {
            db.transactionQueries.DELETEAllTransactions()
            transactions.forEach { insert(it) }
        }
    }

    private fun insert(transaction: Transaction) = db.transactionQueries.INSERTOrReplace(
        id = transaction.id,
        account_id = transaction.accountId,
        source_id = transaction.sourceId,
        email_message_id = transaction.emailMessageId,
        thread_id = transaction.threadId,
        reference = transaction.reference,
        date = transaction.date,
        amount = transaction.amount,
        currency = transaction.currency,
        type = transaction.type?.name,
        kind = transaction.kind,
        category = transaction.category,
        description = transaction.description,
        merchant = transaction.merchant,
        created_at = transaction.createdAt,
        updated_at = transaction.updatedAt,
        deleted = if (transaction.deleted) 1L else 0L,
    )

    private fun updateParsed(transaction: Transaction) = db.transactionQueries.UPDATEParsedById(
        source_id = transaction.sourceId,
        email_message_id = transaction.emailMessageId,
        thread_id = transaction.threadId,
        reference = transaction.reference,
        date = transaction.date,
        amount = transaction.amount,
        currency = transaction.currency,
        type = transaction.type?.name,
        kind = transaction.kind,
        description = transaction.description,
        merchant = transaction.merchant,
        updated_at = transaction.updatedAt,
        id = transaction.id,
    )

    private fun Transactions.toModel() = Transaction(
        id = id,
        accountId = account_id,
        sourceId = source_id,
        emailMessageId = email_message_id,
        threadId = thread_id,
        reference = reference,
        date = date,
        amount = amount,
        currency = currency,
        type = type?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() },
        kind = kind,
        category = category,
        description = description,
        merchant = merchant,
        createdAt = created_at,
        updatedAt = updated_at,
        deleted = deleted != 0L,
    )
}
