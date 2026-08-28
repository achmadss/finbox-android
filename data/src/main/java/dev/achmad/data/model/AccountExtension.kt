package dev.achmad.data.model

/** Per-account extension assignment; [position] defines the match order (first match wins). */
data class AccountExtension(
    val accountId: String,
    val extensionId: Long,
    val enabled: Boolean,
    val position: Int,
)
