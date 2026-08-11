package dev.achmad.data.model

/** Per-account parser assignment; [position] defines the match order (first match wins). */
data class AccountExtension(
    val accountId: String,
    val sourceId: Long,
    val enabled: Boolean,
    val position: Int,
)
