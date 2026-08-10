package dev.achmad.domain.repository

import dev.achmad.domain.model.AccountExtension
import kotlinx.coroutines.flow.Flow

interface AccountExtensionRepository {
    fun forAccount(accountId: String): Flow<List<AccountExtension>>
    fun allAssignments(): Flow<List<AccountExtension>>
    suspend fun setEnabled(accountId: String, sourceId: Long, enabled: Boolean)
    suspend fun deleteForAccount(accountId: String)
    suspend fun reorder(accountId: String, sourceIds: List<Long>)
}
