package dev.achmad.data.backup

import dev.achmad.data.model.AccountExtension
import dev.achmad.data.model.Email
import dev.achmad.data.model.EmailAccount
import dev.achmad.data.model.InstalledExtension
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionType
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Everything the app stores, as one document. */
@Serializable
data class BackupData(
    val version: Int = FORMAT_VERSION,
    val createdAt: Long = 0L,
    val accounts: List<BackupAccount> = emptyList(),
    val assignments: List<BackupAssignment> = emptyList(),
    val extensions: List<BackupExtension> = emptyList(),
    val emails: List<BackupEmail> = emptyList(),
    val transactions: List<BackupTransaction> = emptyList(),
)

@Serializable
data class BackupAccount(
    val id: String,
    val email: String,
    val displayName: String? = null,
    val authTokenRef: String? = null,
    val enabled: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastSyncAt: Long? = null,
    val lastHistoryId: String? = null,
    val syncQuery: String? = null,
    val importCursor: String? = null,
    val importedBackTo: Long? = null,
)

@Serializable
data class BackupAssignment(
    val accountId: String,
    val sourceId: Long,
    val enabled: Boolean = true,
    val position: Int = 0,
)

@Serializable
data class BackupExtension(
    val pkg: String,
    val provider: String,
    val name: String,
    val file: String,
    val versionCode: Int,
    val versionName: String,
    val libVersion: String,
    val sha256: String,
    val sourceIds: List<Long> = emptyList(),
    val enabled: Boolean = true,
)

@Serializable
data class BackupEmail(
    val messageId: String,
    val accountId: String,
    val threadId: String? = null,
    val from: String = "",
    val subject: String = "",
    val date: Long = 0L,
    val triedSourceIds: List<Long> = emptyList(),
    val parsedBySourceId: Long? = null,
    val fetchedAt: Long = 0L,
)

@Serializable
data class BackupTransaction(
    val id: String,
    val accountId: String,
    val sourceId: Long,
    val emailMessageId: String,
    val threadId: String? = null,
    val reference: String? = null,
    val date: Long? = null,
    val amount: Long? = null,
    val currency: String? = null,
    val type: String? = null,
    val kind: String? = null,
    val category: String? = null,
    val description: String? = null,
    val merchant: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val deleted: Boolean = false,
)

/** What the app writes and reads back: gzipped JSON, `.finboxbackup`. */
const val BACKUP_EXTENSION = "finboxbackup"

/** Bumped when a released format can no longer be read as-is. */
const val FORMAT_VERSION = 1

/**
 * Whole-app backup and restore.
 *
 * The file is gzipped JSON so a future version can still read an old one:
 * unknown fields are ignored and missing ones fall back to defaults. CSV is
 * for handing data to a spreadsheet, not for this — it can't carry the whole
 * shape without losing types.
 *
 * Restore replaces everything. OAuth tokens are not in here: they live in
 * Keystore-backed storage, so a restored account has its settings and history
 * but has to be signed in again.
 */
class FinboxBackup(
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

    /** Writes the whole database to [out]. Does not close it. */
    suspend fun backupTo(out: OutputStream) = withContext(Dispatchers.IO) {
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

    /** Reads a backup without applying it — for showing what's in a file. */
    suspend fun read(input: InputStream): BackupData = withContext(Dispatchers.IO) {
        val text = GZIPInputStream(input).use { it.readBytes().decodeToString() }
        json.decodeFromString<BackupData>(text)
    }

    /** Replaces everything with the contents of [input]. */
    suspend fun restoreFrom(input: InputStream) {
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
}

private fun EmailAccount.toBackup() = BackupAccount(
    id, email, displayName, authTokenRef, enabled, createdAt, updatedAt,
    lastSyncAt, lastHistoryId, syncQuery, importCursor, importedBackTo,
)

private fun BackupAccount.toModel() = EmailAccount(
    id, email, displayName, authTokenRef, enabled, createdAt, updatedAt,
    lastSyncAt, lastHistoryId, syncQuery, importCursor, importedBackTo,
)

private fun AccountExtension.toBackup() = BackupAssignment(accountId, sourceId, enabled, position)

private fun BackupAssignment.toModel() = AccountExtension(accountId, sourceId, enabled, position)

private fun InstalledExtension.toBackup() = BackupExtension(
    pkg, provider, name, file, versionCode, versionName, libVersion, sha256, sourceIds, enabled,
)

private fun BackupExtension.toModel() = InstalledExtension(
    pkg, provider, name, file, versionCode, versionName, libVersion, sha256, sourceIds, enabled,
)

private fun Email.toBackup() = BackupEmail(
    messageId = messageId,
    threadId = threadId,
    accountId = accountId,
    from = from,
    subject = subject,
    date = date,
    triedSourceIds = triedSourceIds,
    parsedBySourceId = parsedBySourceId,
    fetchedAt = fetchedAt,
)

private fun BackupEmail.toModel() = Email(
    messageId = messageId,
    threadId = threadId,
    accountId = accountId,
    from = from,
    subject = subject,
    date = date,
    // Bodies are deliberately not in a backup — they are most of the database,
    // and a restored email only needs one again if a parser change re-reads it,
    // which fetches and stores it then.
    bodyHtml = null,
    triedSourceIds = triedSourceIds,
    parsedBySourceId = parsedBySourceId,
    fetchedAt = fetchedAt,
)

private fun Transaction.toBackup() = BackupTransaction(
    id = id,
    accountId = accountId,
    sourceId = sourceId,
    emailMessageId = emailMessageId,
    threadId = threadId,
    reference = reference,
    date = date,
    amount = amount,
    currency = currency,
    type = type?.name,
    kind = kind,
    category = category,
    description = description,
    merchant = merchant,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deleted = deleted,
)

private fun BackupTransaction.toModel() = Transaction(
    id = id,
    accountId = accountId,
    sourceId = sourceId,
    emailMessageId = emailMessageId,
    threadId = threadId,
    reference = reference,
    date = date,
    amount = amount,
    currency = currency,
    type = type?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() },
    kind = kind,
    category = category,
    description = description,
    merchant = merchant,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deleted = deleted,
)
