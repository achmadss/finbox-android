package dev.achmad.data.backup

import dev.achmad.data.model.AccountExtension
import dev.achmad.data.model.CategorySource
import dev.achmad.data.model.EmailAccount
import dev.achmad.data.model.InstalledExtension
import dev.achmad.data.model.StoredEmail
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionDirection
import dev.achmad.data.model.transactionIndexOf

// Between the database and the file. Their own file because they are the only
// part a format bump touches.

internal fun EmailAccount.toBackup() = BackupAccount(
    id, email, displayName, authTokenRef, enabled, createdAt, updatedAt,
    lastSyncAt, lastHistoryId, syncQuery, importCursor, importedBackTo,
)

internal fun BackupAccount.toModel() = EmailAccount(
    id, email, displayName, authTokenRef, enabled, createdAt, updatedAt,
    lastSyncAt, lastHistoryId, syncQuery, importCursor, importedBackTo,
)

internal fun AccountExtension.toBackup() = BackupAssignment(accountId, extensionId, enabled, position)

internal fun BackupAssignment.toModel() = AccountExtension(accountId, extensionId, enabled, position)

internal fun InstalledExtension.toBackup() = BackupExtension(
    pkg, provider, name, file, versionCode, versionName, libVersion, sha256, extensionIds, enabled,
)

internal fun BackupExtension.toModel() = InstalledExtension(
    pkg, provider, name, file, versionCode, versionName, libVersion, sha256, extensionIds, enabled,
)

internal fun StoredEmail.toBackup() = BackupEmail(
    messageId = messageId,
    threadId = threadId,
    accountId = accountId,
    from = from,
    subject = subject,
    date = date,
    triedExtensionIds = triedExtensionIds,
    parsedByExtensionId = parsedByExtensionId,
    fetchedAt = fetchedAt,
)

internal fun BackupEmail.toModel() = StoredEmail(
    messageId = messageId,
    threadId = threadId,
    accountId = accountId,
    from = from,
    subject = subject,
    date = date,
    // Bodies are deliberately not backed up: they are most of the database and
    // get refetched when an extension change re-reads them.
    body = null,
    triedExtensionIds = triedExtensionIds,
    parsedByExtensionId = parsedByExtensionId,
    fetchedAt = fetchedAt,
)

internal fun Transaction.toBackup() = BackupTransaction(
    id = id,
    accountId = accountId,
    extensionId = extensionId,
    emailMessageId = emailMessageId,
    threadId = threadId,
    reference = reference,
    date = date,
    amount = amount,
    currency = currency,
    direction = direction?.name,
    method = method,
    category = categoryName,
    categorySource = categorySource?.name,
    description = description,
    merchant = merchant,
    createdAt = createdAt,
    updatedAt = updatedAt,
    editedAt = editedAt,
    deleted = deleted,
)

internal fun BackupTransaction.toModel() = Transaction(
    accountId = accountId,
    extensionId = extensionId,
    emailMessageId = emailMessageId,
    // The backup carries the whole id, so an old file restores under the
    // identity it had.
    index = transactionIndexOf(id),
    threadId = threadId,
    reference = reference,
    date = date,
    amount = amount,
    currency = currency,
    direction = direction?.let { runCatching { TransactionDirection.valueOf(it) }.getOrNull() },
    method = method,
    categoryName = category,
    categorySource = CategorySource.fromStringOrNull(categorySource),
    description = description,
    merchant = merchant,
    createdAt = createdAt,
    updatedAt = updatedAt,
    editedAt = editedAt,
    deleted = deleted,
)
