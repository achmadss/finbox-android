package dev.achmad.data.repository

import dev.achmad.data.db.FinboxDatabase
import dev.achmad.data.model.StoredEmail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.achmad.data.db.Email as EmailRow

class EmailRepository(
    private val db: FinboxDatabase,
) {

    suspend fun all(): List<StoredEmail> = withContext(Dispatchers.IO) {
        db.emailQueries.SELECTAllEmails().executeAsList().map { it.toModel() }
    }

    suspend fun forAccount(accountId: String): List<StoredEmail> = withContext(Dispatchers.IO) {
        db.emailQueries.SELECTForAccount(accountId).executeAsList().map { it.toModel() }
    }

    suspend fun unparsed(): List<StoredEmail> = withContext(Dispatchers.IO) {
        db.emailQueries.SELECTUnparsed().executeAsList().map { it.toModel() }
    }

    /** Emails one of [extensionIds] claimed — what a change to it re-reads. */
    suspend fun parsedBy(extensionIds: Collection<Long>): List<StoredEmail> = withContext(Dispatchers.IO) {
        if (extensionIds.isEmpty()) return@withContext emptyList()
        db.emailQueries.SELECTByExtension(extensionIds).executeAsList().map { it.toModel() }
    }

    /** Stores the emails that aren't here yet, returning how many were new. */
    suspend fun insertNew(emails: List<StoredEmail>): Int = withContext(Dispatchers.IO) {
        if (emails.isEmpty()) return@withContext 0
        var added = 0
        db.transaction {
            // The (account_id, message_id) key already rejects what's here, so
            // this counts rather than looking every message up first.
            val accountIds = emails.map { it.accountId }.distinct()
            val before = accountIds.sumOf { db.emailQueries.CountForAccount(it).executeAsOne() }
            for (email in emails) {
                db.emailQueries.INSERTOrIgnore(
                    message_id = email.messageId,
                    thread_id = email.threadId,
                    account_id = email.accountId,
                    sender = email.from,
                    subject = email.subject,
                    date = email.date,
                    body = email.body,
                    tried_extension_ids = email.triedExtensionIds.joinToString(" "),
                    parsed_by_extension_id = email.parsedByExtensionId,
                    fetched_at = email.fetchedAt,
                )
            }
            val after = accountIds.sumOf { db.emailQueries.CountForAccount(it).executeAsOne() }
            added = (after - before).toInt()
        }
        added
    }

    suspend fun updateAll(emails: List<StoredEmail>) = withContext(Dispatchers.IO) {
        db.transaction {
            for (email in emails) {
                db.emailQueries.SETParseState(
                    thread_id = email.threadId,
                    body = email.body,
                    tried_extension_ids = email.triedExtensionIds.joinToString(" "),
                    parsed_by_extension_id = email.parsedByExtensionId,
                    account_id = email.accountId,
                    message_id = email.messageId,
                )
            }
        }
    }

    /** Restore path: replaces everything. */
    suspend fun replaceAll(emails: List<StoredEmail>) = withContext(Dispatchers.IO) {
        db.transaction {
            db.emailQueries.DELETEAllEmails()
            for (email in emails) {
                db.emailQueries.INSERTOrReplace(
                    message_id = email.messageId,
                    thread_id = email.threadId,
                    account_id = email.accountId,
                    sender = email.from,
                    subject = email.subject,
                    date = email.date,
                    body = email.body,
                    tried_extension_ids = email.triedExtensionIds.joinToString(" "),
                    parsed_by_extension_id = email.parsedByExtensionId,
                    fetched_at = email.fetchedAt,
                )
            }
        }
    }

    suspend fun deleteForAccount(accountId: String) = withContext(Dispatchers.IO) {
        db.emailQueries.DELETEForAccount(accountId)
        Unit
    }

    private fun EmailRow.toModel() = StoredEmail(
        messageId = message_id,
        threadId = thread_id,
        accountId = account_id,
        from = sender,
        subject = subject,
        date = date,
        body = body,
        triedExtensionIds = tried_extension_ids.split(" ").mapNotNull(String::toLongOrNull),
        parsedByExtensionId = parsed_by_extension_id,
        fetchedAt = fetched_at,
    )
}
