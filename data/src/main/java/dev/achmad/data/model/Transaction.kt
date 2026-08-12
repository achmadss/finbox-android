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
)
