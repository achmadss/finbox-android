package dev.achmad.data.repository

import dev.achmad.data.db.FinboxDatabase
import dev.achmad.data.model.Email
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.achmad.data.db.Email as EmailRow

class EmailRepository(
    private val db: FinboxDatabase,
) {

    suspend fun all(): List<Email> = withContext(Dispatchers.IO) {
        db.emailQueries.SELECTAllEmails().executeAsList().map { it.toModel() }
    }

    suspend fun forAccount(accountId: String): List<Email> = withContext(Dispatchers.IO) {
        db.emailQueries.SELECTForAccount(accountId).executeAsList().map { it.toModel() }
    }

    /** Emails no parser has claimed yet. */
    suspend fun unparsed(): List<Email> = withContext(Dispatchers.IO) {
        db.emailQueries.SELECTUnparsed().executeAsList().map { it.toModel() }
    }

    /**
     * Stores the emails that aren't here yet.
     *
     * @return how many were new.
     */
    suspend fun insertNew(emails: List<Email>): Int = withContext(Dispatchers.IO) {
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
                    tried_source_ids = email.triedSourceIds.joinToString(" "),
                    parsed_by_source_id = email.parsedBySourceId,
                    fetched_at = email.fetchedAt,
                )
            }
            val after = accountIds.sumOf { db.emailQueries.CountForAccount(it).executeAsOne() }
            added = (after - before).toInt()
        }
        added
    }

    /** Writes back parse state for several emails at once. */
    suspend fun updateAll(emails: List<Email>) = withContext(Dispatchers.IO) {
        db.transaction {
            for (email in emails) {
                db.emailQueries.SETParseState(
                    thread_id = email.threadId,
                    tried_source_ids = email.triedSourceIds.joinToString(" "),
                    parsed_by_source_id = email.parsedBySourceId,
                    account_id = email.accountId,
                    message_id = email.messageId,
                )
            }
        }
    }

    /** Restore path: replaces everything. */
    suspend fun replaceAll(emails: List<Email>) = withContext(Dispatchers.IO) {
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
                    tried_source_ids = email.triedSourceIds.joinToString(" "),
                    parsed_by_source_id = email.parsedBySourceId,
                    fetched_at = email.fetchedAt,
                )
            }
        }
    }

    suspend fun deleteForAccount(accountId: String) = withContext(Dispatchers.IO) {
        db.emailQueries.DELETEForAccount(accountId)
        Unit
    }

    private fun EmailRow.toModel() = Email(
        messageId = message_id,
        threadId = thread_id,
        accountId = account_id,
        from = sender,
        subject = subject,
        date = date,
        triedSourceIds = tried_source_ids.split(" ").mapNotNull(String::toLongOrNull),
        parsedBySourceId = parsed_by_source_id,
        fetchedAt = fetched_at,
    )
}
