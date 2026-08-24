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
     * Writes parsed transactions, updating parser-owned fields for an existing id.
     *
     * An id is derived from the email, so re-parsing a message refreshes the rows
     * it already wrote instead of adding more. Two different messages reporting
     * one transaction — an alert and a receipt, say — are caught by the
     * provider reference: the same reference from the same parser is the same
     * transaction, and the row already stored wins, so user-owned fields such as
     * the category survive.
     *
     * A row the user has edited is skipped outright. Their version is the more
     * correct one, and a marker that said "edited" over silently reverted
     * values would be a lie. The cost is deliberate: a parser bugfix will not
     * reach an edited row, and the way out is deleting it so the next import
     * parses it fresh.
     */
    suspend fun upsertAll(transactions: List<Transaction>) = withContext(Dispatchers.IO) {
        db.transaction {
            transactions.forEach { transaction ->
                val existing = db.transactionQueries.SELECTById(transaction.id).executeAsOneOrNull()
                    ?: transaction.reference?.let { reference ->
                        db.transactionQueries
                            .SELECTByReference(transaction.accountId, transaction.parserId, reference)
                            .executeAsOneOrNull()
                    }
                when {
                    existing == null -> insert(transaction)
                    // Hand-edited: the user's version wins over anything a
                    // parser reads, this time and every time after.
                    existing.edited_at != null -> Unit
                    // Same email again: refresh the row it wrote, under whatever
                    // id it already has.
                    existing.email_message_id == transaction.emailMessageId ->
                        updateParsed(transaction, existing.id)
                    // A duplicate from another message: leave the stored row alone.
                    else -> Unit
                }
            }
        }
    }

    /**
     * The edit path: everything the user owns, stamped as a hand edit.
     *
     * Stamping [Transaction.editedAt] here is what marks the row in the list
     * and what makes [upsertAll] leave it alone from now on.
     */
    suspend fun update(transaction: Transaction) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.transactionQueries.UPDATEById(
            reference = transaction.reference,
            date = transaction.date,
            amount = transaction.amount,
            currency = transaction.currency,
            direction = transaction.direction?.name,
            method = transaction.method,
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
     * Counts as an edit, so these survive a re-parse and a classify pass that
     * was not told to replace manual work.
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
     * Leaves [Transaction.editedAt] alone: the marker says the user edited the
     * transaction, which stays true even when a run replaces the category. A
     * [source] of null goes with [TransactionCategory.UNKNOWN] — nobody decided
     * that, code observed it — and is what lets a later pass re-evaluate the row
     * once the missing merchant or description turns up.
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
     * The cache only reaches rows classified after an answer existed, so
     * correcting one INDOMARET row leaves the other thirty-nine wrong. This is
     * what the offer to apply a correction backwards is counted from.
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
     * The cache is the transactions table — a signature that has been
     * classified once is stored in every row carrying it, so a separate table
     * would only duplicate what is already there. The user's own answer wins
     * over a model's; between two of the same kind, the most recent does.
     *
     * ponytail: grouped in memory rather than matched in SQL, because the
     * normalization behind a signature is a Kotlin function and duplicating it
     * as SQL is how the two drift apart. Fine while a ledger is thousands of
     * rows; if it stops being fine, store the normalized key as a column and
     * index it rather than writing the normalization twice.
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

    /**
     * Drops everything a parser parsed under one method — what switching that method
     * off means, since a re-parse will not write them back.
     */
    suspend fun deleteByMethod(parserIds: Collection<Long>, method: String) = withContext(Dispatchers.IO) {
        db.transaction {
            parserIds.forEach { db.transactionQueries.DELETEByMethod(it, method) }
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
        parser_id = transaction.parserId,
        email_message_id = transaction.emailMessageId,
        thread_id = transaction.threadId,
        reference = transaction.reference,
        date = transaction.date,
        amount = transaction.amount,
        currency = transaction.currency,
        direction = transaction.direction?.name,
        method = transaction.method,
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
        parser_id = transaction.parserId,
        email_message_id = transaction.emailMessageId,
        thread_id = transaction.threadId,
        reference = transaction.reference,
        date = transaction.date,
        amount = transaction.amount,
        currency = transaction.currency,
        direction = transaction.direction?.name,
        method = transaction.method,
        description = transaction.description,
        merchant = transaction.merchant,
        updated_at = transaction.updatedAt,
        id = id,
    )

    private fun Transactions.toModel() = Transaction(
        accountId = account_id,
        parserId = parser_id,
        emailMessageId = email_message_id,
        // The model derives its id from these four, so the number has to come back
        // out of the stored one — nothing else records it.
        index = transactionIndexOf(id),
        threadId = thread_id,
        reference = reference,
        date = date,
        amount = amount,
        currency = currency,
        direction = direction?.let { runCatching { TransactionDirection.valueOf(it) }.getOrNull() },
        method = method,
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
