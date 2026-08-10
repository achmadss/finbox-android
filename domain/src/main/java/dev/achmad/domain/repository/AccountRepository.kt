package dev.achmad.domain.repository

import dev.achmad.domain.model.EmailAccount
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun accounts(): Flow<List<EmailAccount>>
    suspend fun getById(id: String): EmailAccount?
    suspend fun upsert(account: EmailAccount)
    suspend fun delete(id: String)
    suspend fun setEnabled(id: String, enabled: Boolean)
    suspend fun updateLastSync(id: String, at: Long)
}
