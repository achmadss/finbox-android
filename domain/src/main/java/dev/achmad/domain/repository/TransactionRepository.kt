package dev.achmad.domain.repository

import dev.achmad.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun transactions(): Flow<List<Transaction>>
    fun search(query: String): Flow<List<Transaction>>
    suspend fun getById(id: String): Transaction?
    /** Inserts a transaction, ignoring rows that violate the dedup constraints. */
    suspend fun insertIgnoringDuplicates(transaction: Transaction): Boolean
    suspend fun update(transaction: Transaction)
    suspend fun delete(id: String)
}
