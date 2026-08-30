package dev.achmad.data.backup

import dev.achmad.data.model.AccountSource
import dev.achmad.data.model.CategorySource
import dev.achmad.data.model.EmailAccount
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

internal fun AccountSource.toBackup() = BackupAssignment(accountId, sourceId, enabled, position)

internal fun BackupAssignment.toModel() = AccountSource(accountId, sourceId, enabled, position)

internal fun StoredEmail.toBackup() = BackupEmail(
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

internal fun BackupEmail.toModel() = StoredEmail(
    messageId = messageId,
    threadId = threadId,
    accountId = accountId,
    from = from,
    subject = subject,
    date = date,
    // Bodies are deliberately not backed up: they are most of the database and
    // get refetched when a source change re-reads them.
    body = null,
    triedSourceIds = triedSourceIds,
    parsedBySourceId = parsedBySourceId,
    fetchedAt = fetchedAt,
)

internal fun Transaction.toBackup() = BackupTransaction(
    id = id,
    accountId = accountId,
    sourceId = sourceId,
    emailMessageId = emailMessageId,
    threadId = threadId,
    reference = reference,
    date = date,
    amount = amount,
    currency = currency,
    direction = direction?.name,
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
    sourceId = sourceId,
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
    categoryName = category,
    categorySource = CategorySource.fromStringOrNull(categorySource),
    description = description,
    merchant = merchant,
    createdAt = createdAt,
    updatedAt = updatedAt,
    editedAt = editedAt,
    deleted = deleted,
)
