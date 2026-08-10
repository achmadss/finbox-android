package dev.achmad.domain.model

enum class UnrecognizedStatus { UNREVIEWED, REVIEWED, MANUALLY_MAPPED }

data class UnrecognizedEmail(
    val id: String,
    val accountId: String,
    val emailMessageId: String,
    val subject: String?,
    val sender: String?,
    val receivedAt: Long?,
    val reason: String?,
    val status: UnrecognizedStatus,
    val bodyRef: String?,
    val createdAt: Long,
)
