package dev.achmad.data.repository
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList

import dev.achmad.data.db.FinboxDatabase
import dev.achmad.data.db.Transactions
import dev.achmad.domain.model.Transaction
import dev.achmad.domain.model.TransactionType
import dev.achmad.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TransactionRepositoryImpl(
    private val db: FinboxDatabase,
) : TransactionRepository {

    override fun transactions(): Flow<List<Transaction>> =
        db.transactionQueries.SELECTAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toModel() } }

    override fun search(query: String): Flow<List<Transaction>> {
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

    override suspend fun getById(id: String): Transaction? = withContext(Dispatchers.IO) {
        db.transactionQueries.SELECTById(id).executeAsOneOrNull()?.toModel()
    }

    override suspend fun insertIgnoringDuplicates(transaction: Transaction): Boolean =
        withContext(Dispatchers.IO) {
            val exists = db.transactionQueries.SELECTById(transaction.id).executeAsOneOrNull() != null
            if (!exists) {
                db.transactionQueries.INSERTOrIgnore(
                id = transaction.id,
                account_id = transaction.accountId,
                source_id = transaction.sourceId,
                parser_id = transaction.parserId,
                email_message_id = transaction.emailMessageId,
                reference = transaction.reference,
                date = transaction.date,
                amount = transaction.amount,
                currency = transaction.currency,
                type = transaction.type?.name,
                category = transaction.category,
                description = transaction.description,
                merchant = transaction.merchant,
                created_at = transaction.createdAt,
                updated_at = transaction.updatedAt,
                deleted = if (transaction.deleted) 1L else 0L,
                )
            }
            !exists
        }

    override suspend fun update(transaction: Transaction) = withContext(Dispatchers.IO) {
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

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        db.transactionQueries.DELETEById(System.currentTimeMillis(), id)
        Unit
    }

    private fun Transactions.toModel() = Transaction(
        id = id,
        accountId = account_id,
        sourceId = source_id,
        parserId = parser_id,
        emailMessageId = email_message_id,
        reference = reference,
        date = date,
        amount = amount,
        currency = currency,
        type = type?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() },
        category = category,
        description = description,
        merchant = merchant,
        createdAt = created_at,
        updatedAt = updated_at,
        deleted = deleted != 0L,
    )
}
