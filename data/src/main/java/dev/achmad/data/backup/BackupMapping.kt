package dev.achmad.data.backup

import dev.achmad.data.model.AccountParser
import dev.achmad.data.model.EmailAccount
import dev.achmad.data.model.InstalledParser
import dev.achmad.data.model.StoredEmail
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionDirection
import dev.achmad.data.model.transactionIndexOf

// Between what the database holds and what the file carries. Their own file
// because they are the part that has to change whenever a model gains a column,
// and the only part a format bump touches.

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
    // Bodies are deliberately not in a backup — they are most of the database,
    // and a restored email only needs one again if a parser change re-reads it,
    // which fetches and stores it then.
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
    category = category,
    description = description,
    merchant = merchant,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deleted = deleted,
)

internal fun BackupTransaction.toModel() = Transaction(
    accountId = accountId,
    parserId = parserId,
    emailMessageId = emailMessageId,
    // The backup still carries the whole id, so a file written by an older build
    // restores under the identity it had.
    index = transactionIndexOf(id),
    threadId = threadId,
    reference = reference,
    date = date,
    amount = amount,
    currency = currency,
    direction = direction?.let { runCatching { TransactionDirection.valueOf(it) }.getOrNull() },
    method = method,
    category = category,
    description = description,
    merchant = merchant,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deleted = deleted,
)
