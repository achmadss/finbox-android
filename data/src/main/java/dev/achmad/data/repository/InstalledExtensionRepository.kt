package dev.achmad.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.achmad.data.db.FinboxDatabase
import dev.achmad.data.db.Installed_extension
import dev.achmad.data.model.InstalledExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class InstalledExtensionRepository(
    private val db: FinboxDatabase,
) {

    fun extensions(): Flow<List<InstalledExtension>> =
        db.installedExtensionQueries.SELECTAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toModel() } }

    suspend fun getByPkg(pkg: String): InstalledExtension? = withContext(Dispatchers.IO) {
        db.installedExtensionQueries.SELECTByPkg(pkg).executeAsOneOrNull()?.toModel()
    }

    suspend fun upsert(extension: InstalledExtension) = withContext(Dispatchers.IO) {
        upsertIn(extension)
        Unit
    }

    private fun upsertIn(extension: InstalledExtension) =
        db.installedExtensionQueries.INSERTOrReplace(
            pkg = extension.pkg,
            name = extension.name,
            file = extension.file,
            version_code = extension.versionCode.toLong(),
            version_name = extension.versionName,
            lib_version = extension.libVersion,
            sha256 = extension.sha256,
            extension_ids = Json.encodeToString(extension.extensionIds),
            enabled = if (extension.enabled) 1L else 0L,
        )

    suspend fun all(): List<InstalledExtension> = withContext(Dispatchers.IO) {
        db.installedExtensionQueries.SELECTAll().executeAsList().map { it.toModel() }
    }

    /** Restore path: replaces everything. */
    suspend fun replaceAll(extensions: List<InstalledExtension>) = withContext(Dispatchers.IO) {
        db.transaction {
            db.installedExtensionQueries.DELETEAllExtensions()
            extensions.forEach { upsertIn(it) }
        }
    }

    suspend fun delete(pkg: String) = withContext(Dispatchers.IO) {
        db.installedExtensionQueries.DELETEByPkg(pkg)
        Unit
    }

    suspend fun setEnabled(pkg: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        db.installedExtensionQueries.SETEnabled(if (enabled) 1L else 0L, pkg)
        Unit
    }

    private fun Installed_extension.toModel() = InstalledExtension(
        pkg = pkg,
        name = name,
        file = file_,
        versionCode = version_code.toInt(),
        versionName = version_name,
        libVersion = lib_version,
        sha256 = sha256,
        extensionIds = Json.decodeFromString<List<String>>(extension_ids),
        enabled = enabled != 0L,
    )
}
