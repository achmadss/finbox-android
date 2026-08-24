package dev.achmad.data.model

/** Per-account parser assignment; [position] defines the match order (first match wins). */
data class AccountParser(
    val accountId: String,
    val parserId: Long,
    val enabled: Boolean,
    val position: Int,
)
