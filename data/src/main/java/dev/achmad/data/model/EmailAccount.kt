package dev.achmad.data.model

data class EmailAccount(
    val id: String,
    val email: String,
    val displayName: String?,
    val authTokenRef: String?,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSyncAt: Long?,
)
