package dev.achmad.domain.repository

import dev.achmad.domain.model.UnrecognizedEmail
import kotlinx.coroutines.flow.Flow

interface UnrecognizedEmailRepository {
    fun emails(): Flow<List<UnrecognizedEmail>>
    suspend fun insertIgnoringDuplicates(email: UnrecognizedEmail): Boolean
    suspend fun markReviewed(id: String)
    suspend fun delete(id: String)
}
