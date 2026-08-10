package dev.achmad.domain.model

enum class TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER,
}

data class Transaction(
    val id: String,
    val accountId: String,
    val sourceId: Long,
    val parserId: Long,
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
