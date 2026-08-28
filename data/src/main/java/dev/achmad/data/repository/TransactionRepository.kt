package dev.achmad.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.achmad.data.db.FinboxDatabase
import dev.achmad.data.db.Transactions
import dev.achmad.data.model.CategorySource
import dev.achmad.data.model.Signature
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionCategory
import dev.achmad.data.model.TransactionDirection
import dev.achmad.data.model.signature
import dev.achmad.data.model.transactionIndexOf
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

    /**
     * Writes parsed transactions, updating extension-owned fields for an existing id.
     *
     * A re-parsed message refreshes the rows it already wrote. The same
     * reference from the same extension in another message is the same transaction,
     * and the row already stored wins. Rows the user edited are skipped: their
     * version wins over anything an extension reads.
     */
    suspend fun upsertAll(transactions: List<Transaction>) = withContext(Dispatchers.IO) {
        db.transaction {
            transactions.forEach { transaction ->
                val existing = db.transactionQueries.SELECTById(transaction.id).executeAsOneOrNull()
                    ?: transaction.reference?.let { reference ->
                        db.transactionQueries
                            .SELECTByReference(transaction.accountId, transaction.extensionId, reference)
                            .executeAsOneOrNull()
                    }
                when {
                    existing == null -> insert(transaction)
                    // Hand-edited: the user's version wins over any re-parse.
                    existing.edited_at != null -> Unit
                    // Same email again: refresh under the id it already has.
                    existing.email_message_id == transaction.emailMessageId ->
                        updateParsed(transaction, existing.id)
                    // A duplicate from another message: the stored row wins.
                    else -> Unit
                }
            }
        }
    }

    /**
     * The edit path: everything the user owns, stamped as a hand edit.
     *
     * Stamping [Transaction.editedAt] is what makes [upsertAll] leave the row
     * alone from now on.
     */
    suspend fun update(transaction: Transaction) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.transactionQueries.UPDATEById(
            reference = transaction.reference,
            date = transaction.date,
            amount = transaction.amount,
            currency = transaction.currency,
            direction = transaction.direction?.name,
            category = transaction.categoryName,
            category_source = transaction.categorySource?.name,
            description = transaction.description,
            merchant = transaction.merchant,
            updated_at = now,
            edited_at = now,
            id = transaction.id,
        )
        Unit
    }

    /**
     * Files rows under a category by hand — one row, a selection, or all of them.
     *
     * Counts as an edit, so these survive re-parses and passes that do not
     * replace manual work.
     */
    suspend fun setCategoryByUser(
        ids: Collection<String>,
        category: TransactionCategory,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.transaction {
            ids.forEach { db.transactionQueries.UPDATECategoryByUser(category.name, now, it) }
        }
    }

    /**
     * A classify pass writing its own answer.
     *
     * Leaves [Transaction.editedAt] alone, so a run that replaces a category
     * does not unmark a user edit. A null [source] accompanies code-assigned
     * [TransactionCategory.UNKNOWN], letting a later pass re-evaluate the row
     * once the missing text turns up.
     */
    suspend fun setCategory(
        id: String,
        category: TransactionCategory?,
        source: CategorySource?,
    ) = withContext(Dispatchers.IO) {
        db.transactionQueries.UPDATECategoryById(
            category = category?.name,
            category_source = source?.name,
            updated_at = System.currentTimeMillis(),
            id = id,
        )
        Unit
    }

    /**
     * Other rows a classifier would read exactly as it reads this one.
     *
     * The cache only covers rows classified after an answer existed, so
     * correcting one row does not reach its peers; this is the pool an
     * "apply the correction to matching rows" offer draws on.
     */
    suspend fun withSignature(
        signature: Signature,
        excludingId: String,
    ): List<Transaction> = withContext(Dispatchers.IO) {
        db.transactionQueries.SELECTAll().executeAsList()
            .map { it.toModel() }
            .filter { it.id != excludingId && it.signature() == signature }
    }

    /**
     * Every signature somebody has already answered, best answer first.
     *
     * The cache is the transactions table itself: a classified signature is
     * stored in every row carrying it, so a separate table would merely repeat
     * it. The user's answer beats a model's; between two of the same kind, the
     * newest wins.
     */
    suspend fun categoryCache(): Map<Signature, TransactionCategory> = withContext(Dispatchers.IO) {
        // ::Transactions because the WHERE narrows category to non-null, which
        // makes SQLDelight generate a row type of its own that nothing else uses.
        db.transactionQueries.SELECTCategorized(::Transactions).executeAsList()
            .map { it.toModel() }
            .sortedWith(
                compareByDescending<Transaction> { it.categorySource == CategorySource.USER }
                    .thenByDescending { it.updatedAt },
            )
            .mapNotNull { transaction -> transaction.category?.let { transaction.signature() to it } }
            // First wins, and the sort above put the best answer first. toMap
            // would quietly do the opposite.
            .distinctBy { (signature, _) -> signature }
            .toMap()
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
        extension_id = transaction.extensionId,
        email_message_id = transaction.emailMessageId,
        thread_id = transaction.threadId,
        reference = transaction.reference,
        date = transaction.date,
        amount = transaction.amount,
        currency = transaction.currency,
        direction = transaction.direction?.name,
        category = transaction.categoryName,
        category_source = transaction.categorySource?.name,
        description = transaction.description,
        merchant = transaction.merchant,
        created_at = transaction.createdAt,
        updated_at = transaction.updatedAt,
        edited_at = transaction.editedAt,
        deleted = if (transaction.deleted) 1L else 0L,
    )

    private fun updateParsed(transaction: Transaction, id: String) = db.transactionQueries.UPDATEParsedById(
        extension_id = transaction.extensionId,
        email_message_id = transaction.emailMessageId,
        thread_id = transaction.threadId,
        reference = transaction.reference,
        date = transaction.date,
        amount = transaction.amount,
        currency = transaction.currency,
        direction = transaction.direction?.name,
        description = transaction.description,
        merchant = transaction.merchant,
        updated_at = transaction.updatedAt,
        id = id,
    )

    private fun Transactions.toModel() = Transaction(
        accountId = account_id,
        extensionId = extension_id,
        emailMessageId = email_message_id,
        // The model derives its id from these fields; the stored id is the only
        // record of the number.
        index = transactionIndexOf(id),
        threadId = thread_id,
        reference = reference,
        date = date,
        amount = amount,
        currency = currency,
        direction = direction?.let { runCatching { TransactionDirection.valueOf(it) }.getOrNull() },
        categoryName = category,
        categorySource = CategorySource.fromStringOrNull(category_source),
        description = description,
        merchant = merchant,
        createdAt = created_at,
        updatedAt = updated_at,
        editedAt = edited_at,
        deleted = deleted != 0L,
    )
}
