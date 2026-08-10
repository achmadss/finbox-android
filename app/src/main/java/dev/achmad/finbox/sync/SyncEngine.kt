package dev.achmad.finbox.sync

import dev.achmad.domain.model.EmailAccount
import dev.achmad.domain.model.Transaction
import dev.achmad.domain.model.TransactionType
import dev.achmad.domain.model.UnrecognizedEmail
import dev.achmad.domain.model.UnrecognizedStatus
import dev.achmad.domain.repository.AccountExtensionRepository
import dev.achmad.domain.repository.AccountRepository
import dev.achmad.domain.repository.TransactionRepository
import dev.achmad.domain.repository.UnrecognizedEmailRepository
import dev.achmad.finbox.extension.EmailMessage
import dev.achmad.finbox.extension.ExtensionManager
import dev.achmad.finbox.extension.TransactionSource
import dev.achmad.finbox.gmail.GmailApi
import dev.achmad.finbox.gmail.MessageResponse
import java.util.UUID
import kotlinx.coroutines.flow.first

/**
 * Pulls transaction emails for one account and runs the account's enabled
 * parsers in assignment order; the first match claims the email
 * (first-match-wins). Unparsable emails land in the unrecognized queue.
 * Dedup is enforced by the transactions table unique indexes.
 */
class SyncEngine(
    private val extensionManager: ExtensionManager,
    private val accountRepository: AccountRepository,
    private val accountExtensionRepository: AccountExtensionRepository,
    private val transactionRepository: TransactionRepository,
    private val unrecognizedRepository: UnrecognizedEmailRepository,
    private val gmailApi: GmailApi,
) {

    suspend fun syncAll(): Result<Int> = runCatching {
        val accounts = accountRepository.accounts().first()
            .filter { it.enabled }
        var processed = 0
        for (account in accounts) {
            processed += syncAccount(account)
        }
        processed
    }

    suspend fun syncAccount(account: EmailAccount): Int {
        val parsers = enabledParsers(account)
        val messages = gmailApi.listMessages(
            accountId = account.id,
            query = "category:finance",
            maxResults = 100,
        )
        var processed = 0
        for (ref in messages) {
            if (processMessage(account, ref.id, parsers)) processed++
        }
        if (processed > 0) accountRepository.updateLastSync(account.id, System.currentTimeMillis())
        return processed
    }

    private suspend fun processMessage(
        account: EmailAccount,
        messageId: String,
        parsers: List<TransactionSource>,
    ): Boolean {
        val message: MessageResponse = try {
            gmailApi.getMessage(account.id, messageId)
        } catch (e: Exception) {
            return false
        }
        val email = GmailApi.toEmailMessage(message)
        val parser = parsers.firstOrNull { runCatching { it.isEmailForProvider(email) }.getOrDefault(false) }

        if (parser == null) {
            unrecognizedRepository.insertIgnoringDuplicates(
                UnrecognizedEmail(
                    id = UUID.randomUUID().toString(),
                    accountId = account.id,
                    emailMessageId = email.messageId,
                    subject = email.subject,
                    sender = email.from,
                    receivedAt = email.date,
                    reason = "No matching parser found",
                    status = UnrecognizedStatus.UNREVIEWED,
                    bodyRef = null,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            return true
        }

        val parsed = try {
            parser.parseEmail(email)
        } catch (e: Exception) {
            unrecognizedRepository.insertIgnoringDuplicates(
                UnrecognizedEmail(
                    id = UUID.randomUUID().toString(),
                    accountId = account.id,
                    emailMessageId = email.messageId,
                    subject = email.subject,
                    sender = email.from,
                    receivedAt = email.date,
                    reason = "Parser error: ${e.message}",
                    status = UnrecognizedStatus.UNREVIEWED,
                    bodyRef = null,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            return true
        }

        if (parsed.isEmpty()) {
            unrecognizedRepository.insertIgnoringDuplicates(
                UnrecognizedEmail(
                    id = UUID.randomUUID().toString(),
                    accountId = account.id,
                    emailMessageId = email.messageId,
                    subject = email.subject,
                    sender = email.from,
                    receivedAt = email.date,
                    reason = "Parser returned no transactions",
                    status = UnrecognizedStatus.UNREVIEWED,
                    bodyRef = null,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            return true
        }

        val now = System.currentTimeMillis()
        parsed.forEach { tx ->
            transactionRepository.insertIgnoringDuplicates(
                Transaction(
                    id = UUID.randomUUID().toString(),
                    accountId = account.id,
                    sourceId = parser.id,
                    parserId = parser.id,
                    emailMessageId = email.messageId,
                    reference = tx.reference,
                    date = tx.date,
                    amount = tx.amount,
                    currency = tx.currency,
                    type = tx.type?.let {
                        runCatching { TransactionType.valueOf(it.name) }.getOrNull()
                    },
                    category = null,
                    description = tx.description,
                    merchant = tx.merchant,
                    createdAt = now,
                    updatedAt = now,
                    deleted = false,
                ),
            )
        }
        return true
    }

    private suspend fun enabledParsers(account: EmailAccount): List<TransactionSource> {
        val assignments = accountExtensionRepository.forAccount(account.id).first()
            .filter { it.enabled }
            .sortedBy { it.position }
        return assignments.mapNotNull { extensionManager.getById(it.sourceId) }
    }
}
