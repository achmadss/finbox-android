package dev.achmad.data.model

/** Per-account source assignment; [position] defines the match order (first match wins). */
data class AccountSource(
    val accountId: String,
    val sourceId: String,
    val enabled: Boolean,
    val position: Int,
)
