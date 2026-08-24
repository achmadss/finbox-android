package dev.achmad.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.achmad.data.db.FinboxDatabase
import dev.achmad.data.db.Installed_parser
import dev.achmad.data.model.InstalledParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class InstalledParserRepository(
    private val db: FinboxDatabase,
) {

    fun parsers(): Flow<List<InstalledParser>> =
        db.installedParserQueries.SELECTAll()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.toModel() } }

    suspend fun getByPkg(pkg: String): InstalledParser? = withContext(Dispatchers.IO) {
        db.installedParserQueries.SELECTByPkg(pkg).executeAsOneOrNull()?.toModel()
    }

    suspend fun upsert(parser: InstalledParser) = withContext(Dispatchers.IO) {
        upsertIn(parser)
        Unit
    }

    private fun upsertIn(parser: InstalledParser) =
        db.installedParserQueries.INSERTOrReplace(
            pkg = parser.pkg,
            provider = parser.provider,
            name = parser.name,
            file = parser.file,
            version_code = parser.versionCode.toLong(),
            version_name = parser.versionName,
            lib_version = parser.libVersion,
            sha256 = parser.sha256,
            parser_ids = Json.encodeToString(parser.parserIds),
            enabled = if (parser.enabled) 1L else 0L,
        )

    suspend fun all(): List<InstalledParser> = withContext(Dispatchers.IO) {
        db.installedParserQueries.SELECTAll().executeAsList().map { it.toModel() }
    }

    /** Restore path: replaces everything. */
    suspend fun replaceAll(parsers: List<InstalledParser>) = withContext(Dispatchers.IO) {
        db.transaction {
            db.installedParserQueries.DELETEAllParsers()
            parsers.forEach { upsertIn(it) }
        }
    }

    suspend fun delete(pkg: String) = withContext(Dispatchers.IO) {
        db.installedParserQueries.DELETEByPkg(pkg)
        Unit
    }

    suspend fun setEnabled(pkg: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        db.installedParserQueries.SETEnabled(if (enabled) 1L else 0L, pkg)
        Unit
    }

    private fun Installed_parser.toModel() = InstalledParser(
        pkg = pkg,
        provider = provider,
        name = name,
        file = file_,
        versionCode = version_code.toInt(),
        versionName = version_name,
        libVersion = lib_version,
        sha256 = sha256,
        parserIds = Json.decodeFromString<List<Long>>(parser_ids),
        enabled = enabled != 0L,
    )
}
