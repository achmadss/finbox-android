package dev.achmad.data.backup

import dev.achmad.data.model.AccountParser
import dev.achmad.data.model.CategorySource
import dev.achmad.data.model.EmailAccount
import dev.achmad.data.model.InstalledParser
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

internal fun AccountParser.toBackup() = BackupAssignment(accountId, parserId, enabled, position)

internal fun BackupAssignment.toModel() = AccountParser(accountId, parserId, enabled, position)

internal fun InstalledParser.toBackup() = BackupParser(
    pkg, provider, name, file, versionCode, versionName, libVersion, sha256, parserIds, enabled,
)

internal fun BackupParser.toModel() = InstalledParser(
    pkg, provider, name, file, versionCode, versionName, libVersion, sha256, parserIds, enabled,
)

internal fun StoredEmail.toBackup() = BackupEmail(
    messageId = messageId,
    threadId = threadId,
    accountId = accountId,
    from = from,
    subject = subject,
    date = date,
    triedParserIds = triedParserIds,
    parsedByParserId = parsedByParserId,
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
    // get refetched when a parser change re-reads them.
    body = null,
    triedParserIds = triedParserIds,
    parsedByParserId = parsedByParserId,
    fetchedAt = fetchedAt,
)

internal fun Transaction.toBackup() = BackupTransaction(
    id = id,
    accountId = accountId,
    parserId = parserId,
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
    parserId = parserId,
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
