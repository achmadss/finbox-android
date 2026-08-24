package dev.achmad.data.model

/**
 * Which way the money went — all the app itself knows. What the provider calls
 * it (QRIS, a top up) is the parser's vocabulary and lives in [Transaction.type].
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
     * Which of the transactions this email yielded, counted in the order the
     * parser returned them. Part of [id], so it has to survive a re-read: a
     * transaction the user switched off is skipped without renumbering the rest.
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
     * The key of one of the parser's declared types, e.g. `QRIS`. Null on a
     * hand-entered row, which no parser claimed.
     */
    val type: String?,
    val category: String?,
    val description: String?,
    val merchant: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
) {
    /**
     * A stable identity that does not change when the parser version changes.
     *
     * Keyed on the Gmail message, not the thread: a thread can carry unrelated
     * mail and genuinely different transactions, so collapsing one loses money.
     * Two messages reporting the same transaction are caught later, by the
     * provider reference — see
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

/** Blank thread ids and missing ones mean the same thing, so they store the same. */
fun String?.normalizedThreadId(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
