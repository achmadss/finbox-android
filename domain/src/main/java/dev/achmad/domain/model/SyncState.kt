package dev.achmad.domain.model

data class SyncState(
    val accountId: String,
    val historyId: String?,
    val lastFullSync: Long?,
)
