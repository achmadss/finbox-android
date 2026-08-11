package dev.achmad.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.achmad.data.db.Email_account
import dev.achmad.data.db.FinboxDatabase
import dev.achmad.data.model.EmailAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class AccountRepository(
    private val db: FinboxDatabase,
) {

    fun accounts(): Flow<List<EmailAccount>> =
        db.accountQueries.SELECTAllAccounts()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toModel() } }

    suspend fun getById(id: String): EmailAccount? = withContext(Dispatchers.IO) {
        db.accountQueries.SELECTById(id).executeAsOneOrNull()?.toModel()
    }

    suspend fun upsert(account: EmailAccount) = withContext(Dispatchers.IO) {
        db.accountQueries.INSERTAccount(
            id = account.id,
            email = account.email,
            display_name = account.displayName,
            auth_token_ref = account.authTokenRef,
            enabled = if (account.enabled) 1L else 0L,
            created_at = account.createdAt,
            updated_at = account.updatedAt,
            last_sync_at = account.lastSyncAt,
        )
        db.accountQueries.UPDATEAccount(
            email = account.email,
            display_name = account.displayName,
            auth_token_ref = account.authTokenRef,
            enabled = if (account.enabled) 1L else 0L,
            updated_at = account.updatedAt,
            last_sync_at = account.lastSyncAt,
            id = account.id,
        )
        Unit
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        db.accountQueries.DELETEAccount(id)
        Unit
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        db.accountQueries.SETAccountEnabled(if (enabled) 1L else 0L, System.currentTimeMillis(), id)
        Unit
    }

    suspend fun updateLastSync(id: String, at: Long) = withContext(Dispatchers.IO) {
        db.accountQueries.SETLastSync(at, id)
        Unit
    }

    private fun Email_account.toModel() = EmailAccount(
        id = id,
        email = email,
        displayName = display_name,
        authTokenRef = auth_token_ref,
        enabled = enabled != 0L,
        createdAt = created_at,
        updatedAt = updated_at,
        lastSyncAt = last_sync_at,
    )
}
