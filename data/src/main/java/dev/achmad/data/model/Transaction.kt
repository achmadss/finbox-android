package dev.achmad.data.model

/**
 * Which way the money went — all the app itself knows. What a provider calls
 * it is the parser's vocabulary and lives in [Transaction.method].
 */
enum class TransactionDirection {
    INCOMING,
    OUTGOING,
}

/** One transaction a parser read out of an email. */
data class Transaction(
    val accountId: String,
    val parserId: Long,
    /** The [StoredEmail] this was parsed from. */
    val emailMessageId: String,
    /**
     * Which of the transactions this email yielded, in parser order. Part of
     * [id], so it survives a re-read: skipping a dismissed row must not
     * renumber the rest.
     */
    val index: Int,
    /** Collapses duplicate messages in a thread. */
    val threadId: String?,
    val reference: String?,
    val date: Long?,
    val amount: Long?,
    val currency: String?,
    val direction: TransactionDirection?,
    /**
     * The key of one of the parser's declared methods. Null on a hand-entered
     * row, which no parser claimed.
     */
    val method: String?,
    /**
     * A [TransactionCategory] name, or null when nothing has decided yet.
     *
     * Stored free-form and validated on the way out via [category], so a
     * taxonomy change costs a reclassification, not corrupt rows.
     */
    val categoryName: String?,
    /** Who decided [categoryName]. Null means nobody has. */
    val categorySource: CategorySource?,
    val description: String?,
    val merchant: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * When the user last hand-edited any field, or null if they never have.
     *
     * Independent of [categorySource]: fixing a merchant but leaving the
     * category to a model is one case, accepting parsed fields and filing them
     * yourself is the other.
     */
    val editedAt: Long?,
    val deleted: Boolean,
) {
    /** [categoryName] as a category this build knows, or null. */
    val category: TransactionCategory? get() = TransactionCategory.fromStringOrNull(categoryName)

    /** Whether the user has touched this row — what makes a re-parse leave it alone. */
    val edited: Boolean get() = editedAt != null

    /**
     * A stable identity that does not change when the parser version changes.
     *
     * Keyed on the message, not the thread: a thread can carry unrelated mail
     * and different transactions, so collapsing one loses money. Two messages
     * reporting the same transaction are merged later by reference — see
     * [dev.achmad.data.repository.TransactionRepository.upsertAll].
     */
    val id: String get() = "$accountId:message:$emailMessageId:$parserId:$index"

    /** When it happened, falling back to when it was stored. */
    val timestamp: Long get() = date ?: createdAt

    /** Money leaving the account counts as negative. */
    val signedAmount: Long?
        get() = amount?.let { if (direction == TransactionDirection.OUTGOING) -it else it }
}

/**
 * The [Transaction.index] an id carries, for a row read back from the database
 * or a backup — the only place the number is written down.
 */
fun transactionIndexOf(id: String): Int = id.substringAfterLast(':').toIntOrNull() ?: 0

fun String?.normalizedThreadId(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
