package dev.achmad.finbox.core.sync

import dev.achmad.data.model.EmailAccount
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionType
import dev.achmad.data.repository.AccountRepository
import dev.achmad.data.repository.TransactionRepository
import dev.achmad.finbox.core.extension.ExtensionManager
import dev.achmad.finbox.core.extension.LoadedSource
import dev.achmad.finbox.core.gmail.GmailApi
import dev.achmad.finbox.core.gmail.buildGmailQuery
import dev.achmad.finbox.core.gmail.model.MessageResponse
import dev.achmad.finbox.extension.EmailMessage
import dev.achmad.finbox.extension.ParsedTransaction
import java.util.UUID
import kotlinx.coroutines.flow.first

/**
 * Fetches and parses transaction emails, one source at a time.
 *
 * Each source declares its own [dev.achmad.finbox.extension.EmailQuery], so the
 * app asks Gmail for that source's mail specifically rather than downloading a
 * mailbox and offering every message to every source. What comes back is still
 * confirmed with `isEmailForProvider` — a provider sends statements, OTPs and
 * promotions from the same address.
 *
 * Emails nothing claims are dropped. Dedup is enforced by the transactions
 * table's unique indexes.
 */
class SyncEngine(
    private val extensionManager: ExtensionManager,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val gmailApi: GmailApi,
) {

    /**
     * @param after inclusive lower bound, epoch millis; null means no bound
     * @param before inclusive upper bound, epoch millis; null means no bound
     */
    suspend fun syncAll(after: Long? = null, before: Long? = null): Result<Int> = runCatching {
        val accounts = accountRepository.accounts().first().filter { it.enabled }
        var imported = 0
        for (account in accounts) {
            imported += syncAccount(account, after, before)
        }
        imported
    }

    /** @return how many transactions were written. */
    suspend fun syncAccount(
        account: EmailAccount,
        after: Long? = null,
        before: Long? = null,
    ): Int {
        val sources = extensionManager.sources
        if (sources.isEmpty()) return 0

        var imported = 0
        // A message matching two sources' queries is parsed by the first that
        // claims it, so a shared sender can't produce the transaction twice.
        val claimed = mutableSetOf<String>()

        for (source in sources) {
            val query = buildGmailQuery(source.emailQuery, after, before)
            val refs = try {
                gmailApi.listAllMessages(account.id, query)
            } catch (e: Exception) {
                continue
            }
            for (ref in refs) {
                if (!claimed.add(ref.id)) continue
                val written = processMessage(account, ref.id, source, after, before)
                if (written == null) claimed.remove(ref.id) else imported += written
            }
        }

        accountRepository.updateLastSync(account.id, System.currentTimeMillis())
        return imported
    }

    /**
     * @return the number of transactions written, or null if this source did
     * not claim the message — leaving it available to the next source.
     */
    private suspend fun processMessage(
        account: EmailAccount,
        messageId: String,
        source: LoadedSource,
        after: Long?,
        before: Long?,
    ): Int? {
        val message: MessageResponse = try {
            gmailApi.getMessage(account.id, messageId)
        } catch (e: Exception) {
            return 0
        }
        val email = GmailApi.toEmailMessage(message)

        // Gmail's after:/before: are whole days in local time, so the query is
        // deliberately wider than the window the user picked.
        if (after != null && email.date < after) return 0
        if (before != null && email.date > before) return 0

        val claims = runCatching { source.isEmailForProvider(email) }.getOrDefault(false)
        if (!claims) return null

        val parsed = try {
            source.parseEmail(email)
        } catch (e: Exception) {
            return 0
        }
        parsed.forEach { insert(account, source, email, it) }
        return parsed.size
    }

    private suspend fun insert(
        account: EmailAccount,
        source: LoadedSource,
        email: EmailMessage,
        parsed: ParsedTransaction,
    ) {
        val now = System.currentTimeMillis()
        transactionRepository.insertIgnoringDuplicates(
            Transaction(
                id = UUID.randomUUID().toString(),
                accountId = account.id,
                sourceId = source.id,
                parserId = source.id,
                emailMessageId = email.messageId,
                reference = parsed.reference,
                date = parsed.date,
                amount = parsed.amount,
                currency = parsed.currency,
                type = parsed.type?.let {
                    runCatching { TransactionType.valueOf(it.name) }.getOrNull()
                },
                category = null,
                description = parsed.description,
                merchant = parsed.merchant,
                createdAt = now,
                updatedAt = now,
                deleted = false,
            ),
        )
    }
}
