package dev.achmad.finbox.core.sync

import dev.achmad.data.model.EmailAccount
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionType
import dev.achmad.data.repository.AccountExtensionRepository
import dev.achmad.data.repository.AccountRepository
import dev.achmad.data.repository.TransactionRepository
import dev.achmad.finbox.extension.EmailMessage
import dev.achmad.finbox.core.extension.ExtensionManager
import dev.achmad.finbox.core.extension.TransactionSource
import dev.achmad.finbox.core.gmail.GmailApi
import dev.achmad.finbox.core.gmail.model.MessageResponse
import java.util.UUID
import kotlinx.coroutines.flow.first

/**
 * Pulls transaction emails for one account and runs the account's enabled
 * parsers in assignment order; the first match claims the email
 * (first-match-wins). Emails no parser claims are ignored.
 * Dedup is enforced by the transactions table unique indexes.
 */
class SyncEngine(
    private val extensionManager: ExtensionManager,
    private val accountRepository: AccountRepository,
    private val accountExtensionRepository: AccountExtensionRepository,
    private val transactionRepository: TransactionRepository,
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

        // An email no parser recognises is not a transaction; drop it.
        if (parser == null) return true

        val parsed = try {
            parser.parseEmail(email)
        } catch (e: Exception) {
            return true
        }

        if (parsed.isEmpty()) return true

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
