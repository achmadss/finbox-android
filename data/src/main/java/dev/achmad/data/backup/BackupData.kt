package dev.achmad.data.backup

import kotlinx.serialization.Serializable

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
    val extensionId: Long,
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
    val extensionIds: List<Long> = emptyList(),
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
    val triedExtensionIds: List<Long> = emptyList(),
    val parsedByExtensionId: Long? = null,
    val fetchedAt: Long = 0L,
)

@Serializable
data class BackupTransaction(
    val id: String,
    val accountId: String,
    val extensionId: Long,
    val emailMessageId: String,
    val threadId: String? = null,
    val reference: String? = null,
    val date: Long? = null,
    val amount: Long? = null,
    val currency: String? = null,
    val direction: String? = null,
    val method: String? = null,
    val category: String? = null,
    val categorySource: String? = null,
    val description: String? = null,
    val merchant: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val editedAt: Long? = null,
    val deleted: Boolean = false,
)

/** What the app writes and reads back: gzipped JSON, `.finboxbackup`. */
const val BACKUP_FILE_EXTENSION = "finboxbackup"

/** Bumped when a released format can no longer be read as-is. */
const val FORMAT_VERSION = 1
