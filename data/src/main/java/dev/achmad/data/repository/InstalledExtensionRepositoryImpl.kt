package dev.achmad.data.repository
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList

import dev.achmad.data.db.FinboxDatabase
import dev.achmad.data.db.Installed_extension
import dev.achmad.domain.model.InstalledExtension
import dev.achmad.domain.repository.InstalledExtensionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class InstalledExtensionRepositoryImpl(
    private val db: FinboxDatabase,
) : InstalledExtensionRepository {

    override fun extensions(): Flow<List<InstalledExtension>> =
        db.installedExtensionQueries.SELECTAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toModel() } }

    override suspend fun getByPkg(pkg: String): InstalledExtension? = withContext(Dispatchers.IO) {
        db.installedExtensionQueries.SELECTByPkg(pkg).executeAsOneOrNull()?.toModel()
    }

    override suspend fun upsert(extension: InstalledExtension) = withContext(Dispatchers.IO) {
        db.installedExtensionQueries.INSERTOrReplace(
            pkg = extension.pkg,
            provider = extension.provider,
            name = extension.name,
            file = extension.file,
            version_code = extension.versionCode.toLong(),
            version_name = extension.versionName,
            lib_version = extension.libVersion,
            sha256 = extension.sha256,
            source_ids = Json.encodeToString(extension.sourceIds),
            enabled = if (extension.enabled) 1L else 0L,
        )
    }

    override suspend fun delete(pkg: String) = withContext(Dispatchers.IO) {
        db.installedExtensionQueries.DELETEByPkg(pkg)
    }

    override suspend fun setEnabled(pkg: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        db.installedExtensionQueries.SETEnabled(if (enabled) 1L else 0L, pkg)
    }

    private fun Installed_extension.toModel() = InstalledExtension(
        pkg = pkg,
        provider = provider,
        name = name,
        file = file_,
        versionCode = version_code.toInt(),
        versionName = version_name,
        libVersion = lib_version,
        sha256 = sha256,
        sourceIds = Json.decodeFromString<List<Long>>(source_ids),
        enabled = enabled != 0L,
    )
}
