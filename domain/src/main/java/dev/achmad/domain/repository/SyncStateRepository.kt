package dev.achmad.domain.repository

import dev.achmad.domain.model.SyncState

interface SyncStateRepository {
    suspend fun get(accountId: String): SyncState?
    suspend fun upsert(state: SyncState)
}
