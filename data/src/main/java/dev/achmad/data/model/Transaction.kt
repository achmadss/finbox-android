package dev.achmad.data.model

enum class TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER,
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
    val category: String?,
    val description: String?,
    val merchant: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
) {
    /** When it happened — the parsed date, or when we stored it if the parser found none. */
    val timestamp: Long get() = date ?: createdAt

    /** Money leaving the account counts as negative: expenses and transfers out. */
    val signedAmount: Long?
        get() = amount?.let {
            when (type) {
                TransactionType.EXPENSE, TransactionType.TRANSFER -> -it
                else -> it
            }
        }
}
