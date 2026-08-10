package dev.achmad.data.repository

import dev.achmad.data.db.FinboxDatabase
import dev.achmad.data.db.Sync_state
import dev.achmad.domain.model.SyncState
import dev.achmad.domain.repository.SyncStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncStateRepositoryImpl(
    private val db: FinboxDatabase,
) : SyncStateRepository {

    override suspend fun get(accountId: String): SyncState? = withContext(Dispatchers.IO) {
        db.syncStateQueries.SELECTByAccount(accountId).executeAsOneOrNull()?.toModel()
    }

    override suspend fun upsert(state: SyncState) = withContext(Dispatchers.IO) {
        db.syncStateQueries.INSERTOrReplace(
            accountId = state.accountId,
            historyId = state.historyId,
            lastFullSync = state.lastFullSync,
        )
        Unit
    }

    private fun Sync_state.toModel() = SyncState(
        accountId = account_id,
        historyId = history_id,
        lastFullSync = last_full_sync,
    )
}
