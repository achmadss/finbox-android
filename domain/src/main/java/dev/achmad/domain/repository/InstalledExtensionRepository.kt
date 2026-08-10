package dev.achmad.domain.repository

import dev.achmad.domain.model.InstalledExtension
import kotlinx.coroutines.flow.Flow

interface InstalledExtensionRepository {
    fun extensions(): Flow<List<InstalledExtension>>
    suspend fun getByPkg(pkg: String): InstalledExtension?
    suspend fun upsert(extension: InstalledExtension)
    suspend fun delete(pkg: String)
    suspend fun setEnabled(pkg: String, enabled: Boolean)
}
