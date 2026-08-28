package dev.achmad.data.backup

import android.content.Context
import android.net.Uri
import dev.achmad.data.repository.AccountExtensionRepository
import dev.achmad.data.repository.AccountRepository
import dev.achmad.data.repository.EmailRepository
import dev.achmad.data.repository.InstalledExtensionRepository
import dev.achmad.data.repository.TransactionRepository
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Whole-app backup and restore.
 *
 * Gzipped JSON, read forward-compatibly: unknown fields are ignored and
 * missing ones fall back to defaults. Restore replaces everything; OAuth
 * tokens stay in Keystore-backed storage, so a restored account must be
 * signed in again.
 */
class BackupManager(
    private val context: Context,
    private val accounts: AccountRepository,
    private val assignments: AccountExtensionRepository,
    private val extensions: InstalledExtensionRepository,
    private val emails: EmailRepository,
    private val transactions: TransactionRepository,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Writes the whole database to the document at [uri]. */
    suspend fun backupTo(uri: Uri) = openOutput(uri).use { backupTo(it) }

    /** Reads the backup at [uri] without applying it — for showing what's in a file. */
    suspend fun read(uri: Uri): BackupData = openInput(uri).use { read(it) }

    /** Replaces everything with the contents of the document at [uri]. */
    suspend fun restoreFrom(uri: Uri) = openInput(uri).use { restoreFrom(it) }

    private fun openInput(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: error(CANNOT_OPEN)

    private fun openOutput(uri: Uri): OutputStream =
        context.contentResolver.openOutputStream(uri) ?: error(CANNOT_OPEN)

    /** Writes the whole database to [out]. Does not close it. */
    private suspend fun backupTo(out: OutputStream) = withContext(Dispatchers.IO) {
        val data = BackupData(
            createdAt = System.currentTimeMillis(),
            accounts = accounts.all().map { it.toBackup() },
            assignments = assignments.all().map { it.toBackup() },
            extensions = extensions.all().map { it.toBackup() },
            emails = emails.all().map { it.toBackup() },
            transactions = transactions.all().map { it.toBackup() },
        )
        GZIPOutputStream(out).use { it.write(json.encodeToString(data).toByteArray()) }
    }

    private suspend fun read(input: InputStream): BackupData = withContext(Dispatchers.IO) {
        val text = GZIPInputStream(input).use { it.readBytes().decodeToString() }
        requireRestorable(text)
        json.decodeFromString<BackupData>(text)
    }

    private suspend fun restoreFrom(input: InputStream) {
        val data = read(input)
        require(data.version <= FORMAT_VERSION) {
            "Backup format ${data.version} is newer than this app understands"
        }
        accounts.replaceAll(data.accounts.map { it.toModel() })
        extensions.replaceAll(data.extensions.map { it.toModel() })
        assignments.replaceAll(data.assignments.map { it.toModel() })
        emails.replaceAll(data.emails.map { it.toModel() })
        transactions.replaceAll(data.transactions.map { it.toModel() })
    }

    private companion object {
        const val CANNOT_OPEN = "Could not open the file"
    }
}
