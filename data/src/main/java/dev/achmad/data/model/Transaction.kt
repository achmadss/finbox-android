package dev.achmad.data.model

/**
 * Which way the money went — all the app itself knows.
 *
 * What the provider calls it (QRIS, a top up, a BI-Fast transfer) is the
 * parser's vocabulary and lives in [Transaction.kind]; every one of those is
 * one of these two underneath.
 */
enum class TransactionType {
    INCOME,
    EXPENSE,
}

/**
 * One transaction a parser read out of an email.
 *
 * [emailMessageId] points back at the [Email] it came from, and [sourceId] says
 * which parser produced it.
 */
data class Transaction(
    val accountId: String,
    val sourceId: Long,
    /** Gmail's message id of the email this was parsed from. */
    val emailMessageId: String,
    /**
     * Which of the transactions this email yielded, counted in the order the
     * parser returned them. Part of [id], so it has to survive a re-read: a
     * transaction the user switched off is skipped without renumbering the rest.
     */
    val index: Int,
    /** Gmail's conversation id, used to collapse duplicate messages in a thread. */
    val threadId: String?,
    val reference: String?,
    val date: Long?,
    val amount: Long?,
    val currency: String?,
    val type: TransactionType?,
    /** The source's own name for it, e.g. `QRIS`. Null for mail parsed before kinds. */
    val kind: String?,
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
    val id: String get() = "$accountId:message:$emailMessageId:$sourceId:$index"

    /** When it happened — the parsed date, or when we stored it if the parser found none. */
    val timestamp: Long get() = date ?: createdAt

    /** Money leaving the account counts as negative. */
    val signedAmount: Long?
        get() = amount?.let { if (type == TransactionType.EXPENSE) -it else it }
}

/**
 * The [Transaction.index] an id carries, for a row read back from the database
 * or a backup — the only place the number is written down.
 */
fun transactionIndexOf(id: String): Int = id.substringAfterLast(':').toIntOrNull() ?: 0

/** Blank thread ids and missing ones mean the same thing, so they store the same. */
fun String?.normalizedThreadId(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
