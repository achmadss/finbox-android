package dev.achmad.data.model

/**
 * Which way the money went — all the app itself knows.
 *
 * What the provider calls it (QRIS, a top up, a BI-Fast transfer) is the
 * extension's vocabulary and lives in [Transaction.kind]; every one of those is
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
    val id: String,
    val accountId: String,
    val sourceId: Long,
    /** Gmail's message id of the email this was parsed from. */
    val emailMessageId: String,
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
    /** When it happened — the parsed date, or when we stored it if the parser found none. */
    val timestamp: Long get() = date ?: createdAt

    /** Money leaving the account counts as negative. */
    val signedAmount: Long?
        get() = amount?.let { if (type == TransactionType.EXPENSE) -it else it }
}
