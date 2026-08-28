package dev.achmad.data.backup

import kotlinx.serialization.Serializable

/** Everything the app stores, as one document. */
@Serializable
data class BackupData(
    val version: Int = FORMAT_VERSION,
    val createdAt: Long = 0L,
    val accounts: List<BackupAccount> = emptyList(),
    val assignments: List<BackupAssignment> = emptyList(),
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
    val extensionId: String,
    val enabled: Boolean = true,
    val position: Int = 0,
)

@Serializable
data class BackupEmail(
    val messageId: String,
    val accountId: String,
    val threadId: String? = null,
    val from: String = "",
    val subject: String = "",
    val date: Long = 0L,
    val triedExtensionIds: List<String> = emptyList(),
    val parsedByExtensionId: String? = null,
    val fetchedAt: Long = 0L,
)

@Serializable
data class BackupTransaction(
    val id: String,
    val accountId: String,
    val extensionId: String,
    val emailMessageId: String,
    val threadId: String? = null,
    val reference: String? = null,
    val date: Long? = null,
    val amount: Long? = null,
    val currency: String? = null,
    val direction: String? = null,
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

/**
 * What a backup taken before parsers became extensions calls the field that is
 * now `extensionId`.
 *
 * The version number does not separate the two — the format changed without a
 * bump, per the pre-release rule — so the field name is what identifies it.
 */
private const val PRE_REFACTOR_KEY = "\"parserId\""

/**
 * What an extension id looked like while extensions were separate apps.
 *
 * They are short names again (`bri`), so a backup written in between names
 * extensions that no longer exist. Restoring it would file every transaction
 * under an id nothing answers to — the ledger would load and quietly belong to
 * nobody.
 */
private const val PACKAGE_ID_KEY = "dev.achmad.finbox.extension."

const val PRE_REFACTOR_MESSAGE =
    "This backup was taken before parsers became extensions and cannot be restored. " +
        "Its emails would have to be fetched from Gmail again."

const val PACKAGE_ID_MESSAGE =
    "This backup was taken while extensions installed as separate apps and cannot be " +
        "restored. Its emails would have to be fetched from Gmail again."

/**
 * Refuses a backup this build cannot honestly read, by name, before kotlinx
 * reports a missing field nobody can act on.
 *
 * `ignoreUnknownKeys` would drop every `parserId` silently and restore a ledger
 * whose rows belong to no extension, which is worse than refusing. There is no
 * shim: a `@JsonNames("parserId")` left in to survive one transition is exactly
 * the kind of thing still there in two years — and the same goes for rewriting
 * a package name back to a short id on the way in.
 */
fun requireRestorable(text: String) {
    require(PRE_REFACTOR_KEY !in text) { PRE_REFACTOR_MESSAGE }
    require(PACKAGE_ID_KEY !in text) { PACKAGE_ID_MESSAGE }
}
