package dev.achmad.finbox.core.update.transaction

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.achmad.data.db.FinboxDatabase
import dev.achmad.data.model.EmailAccount
import dev.achmad.data.model.StoredEmail
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionCategory
import dev.achmad.data.model.TransactionDirection as DataTransactionDirection
import dev.achmad.data.repository.AccountRepository
import dev.achmad.data.repository.AccountSourceRepository
import dev.achmad.data.repository.CategoryRuleRepository
import dev.achmad.data.repository.EmailRepository
import dev.achmad.data.repository.TransactionRepository
import dev.achmad.finbox.core.gmail.GmailApi
import dev.achmad.finbox.source.core.ParsedTransaction
import dev.achmad.finbox.source.core.SourceEntry
import dev.achmad.finbox.source.core.TransactionDirection
import dev.achmad.finbox.source.core.email.Email
import dev.achmad.finbox.source.core.email.EmailQuery
import dev.achmad.finbox.source.core.email.EmailSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What `SourceManager.reparseIfAppUpdated` eventually runs — the only place a
 * user's edits meet a re-parse unobserved — wired to a real SQLite database.
 *
 * The WorkManager hop in between (interrupting this on the JVM would mean
 * hosting Android's scheduler here for no payoff) is what stays untested; the
 * re-parse itself is not.
 */
class TransactionReparseTest {

    private val now = 1_700_000_000_000L

    private val bri = SourceEntry(
        id = "bri",
        name = "Bank BRI",
        icon = 0,
        source = object : EmailSource {
            override val query = EmailQuery.from("customercare@bri.co.id")
            override suspend fun parse(email: Email): List<ParsedTransaction> = listOf(
                ParsedTransaction(
                    amount = 30_000,
                    currency = "IDR",
                    date = now,
                    direction = TransactionDirection.OUTGOING,
                    merchant = "BANK BRI PARSE",
                ),
            )
        },
    )

    private val account = EmailAccount(
        id = "account",
        email = "me@gmail.com",
        displayName = null,
        authTokenRef = "access-token",
        enabled = true,
        createdAt = now,
        updatedAt = now,
        lastSyncAt = null,
        lastHistoryId = "history-1",
    )

    /** Reparse may never reach Gmail: bodies come from the database. */
    private val gmail = object : GmailApi {
        override suspend fun getProfile(accountId: String): Nothing = error("no network in a reparse test")
        override suspend fun listMessages(
            accountId: String,
            after: Long?,
            before: Long?,
            narrow: String?,
            maxMessages: Int,
        ): Nothing = error("no network in a reparse test")

        override suspend fun listHistory(
            accountId: String,
            startHistoryId: String,
            pageToken: String?,
        ): Nothing = error("no network in a reparse test")

        override suspend fun getEmail(accountId: String, messageId: String): Nothing =
            error("no network in a reparse test")
    }

    private fun stored(messageId: String) = StoredEmail(
        messageId = messageId,
        threadId = null,
        accountId = account.id,
        from = "customercare@bri.co.id",
        subject = "Receipt",
        date = now,
        body = "<html>receipt</html>",
        triedSourceIds = listOf("bri"),
        parsedBySourceId = "bri",
        fetchedAt = now,
    )

    private fun transaction(messageId: String, merchant: String, amount: Long) = Transaction(
        accountId = account.id,
        sourceId = "bri",
        emailMessageId = messageId,
        index = 0,
        threadId = null,
        reference = null,
        date = now,
        amount = amount,
        currency = "IDR",
        direction = DataTransactionDirection.OUTGOING,
        categoryName = null,
        categorySource = null,
        description = null,
        merchant = merchant,
        createdAt = now,
        updatedAt = now,
        editedAt = null,
        deleted = false,
    )

    private fun updater(repositories: Repositories): TransactionUpdater = TransactionUpdater(
        // What the app passes on an app-update reparse: every enabled source.
        sources = { listOf(bri) },
        accountRepository = repositories.accounts,
        accountSourceRepository = repositories.accountSources,
        emailRepository = repositories.emails,
        transactionRepository = repositories.transactions,
        gmailApi = gmail,
        rules = CategoryRuleRepository(repositories.db),
    )

    private fun database(): FinboxDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FinboxDatabase.Schema.create(driver)
        return FinboxDatabase(driver)
    }

    private data class Repositories(
        val db: FinboxDatabase,
        val accounts: AccountRepository,
        val accountSources: AccountSourceRepository,
        val emails: EmailRepository,
        val transactions: TransactionRepository,
    )

    private fun repositories(db: FinboxDatabase) = Repositories(
        db = db,
        accounts = AccountRepository(db),
        accountSources = AccountSourceRepository(db),
        emails = EmailRepository(db),
        transactions = TransactionRepository(db),
    )

    /** Reparse only touches accounts the database knows: see `reparseSources`. */
    private suspend fun seed(repos: Repositories) {
        repos.accounts.upsert(account)
    }

    @Test
    fun `a hand-edited row survives the app-update re-parse`() = runBlocking {
        val db = database()
        val repos = repositories(db)
        seed(repos)
        repos.emails.insertNew(listOf(stored("message-1"), stored("message-2")))
        repos.transactions.upsertAll(
            listOf(
                transaction("message-1", merchant = "EDIT TUJUAN", amount = 25_000),
                transaction("message-2", merchant = "TIDAK DIPARSE", amount = 25_000),
            ),
        )

        // The user fixes the first row — by hand, which is how it becomes
        // half-wrong and half-right at once.
        repos.transactions.update(
            repos.transactions.getById("${account.id}:message:message-1:bri:0")!!
                .copy(merchant = "Ganti Nama", amount = 75_000),
        )

        // The app updates. The source that shipped with it reads the receipt
        // differently — that is the whole point of the re-parse.
        updater(repos).reparseSources(setOf("bri"))

        val edited = repos.transactions.getById("${account.id}:message:message-1:bri:0")!!
        val untouched = repos.transactions.getById("${account.id}:message:message-2:bri:0")!!
        assertEquals("Ganti Nama", edited.merchant)
        assertEquals(75_000L, edited.amount)
        assertTrue(edited.edited)
        // What nobody edited is still the source's to refresh.
        assertEquals("BANK BRI PARSE", untouched.merchant)
        assertEquals(30_000L, untouched.amount)
        assertTrue(!untouched.edited)
    }

    @Test
    fun `a hand-set category survives the app-update re-parse`() = runBlocking {
        val db = database()
        val repos = repositories(db)
        seed(repos)
        repos.emails.insertNew(listOf(stored("message-1")))
        repos.transactions.upsertAll(listOf(transaction("message-1", merchant = "BANK BRI", amount = 25_000)))

        repos.transactions.setCategoryByUser(
            listOf("${account.id}:message:message-1:bri:0"),
            TransactionCategory.FOOD,
        )

        updater(repos).reparseSources(setOf("bri"))

        val stored = repos.transactions.getById("${account.id}:message:message-1:bri:0")!!
        assertEquals(TransactionCategory.FOOD, stored.category)
        assertNotNull(stored.editedAt)
    }

    @Test
    fun `an unrecognised email is re-read but writes nothing`() = runBlocking {
        val db = database()
        val repos = repositories(db)
        seed(repos)
        repos.emails.insertNew(listOf(stored("message-1")))
        repos.transactions.upsertAll(listOf(transaction("message-1", merchant = "BANK BRI", amount = 25_000)))

        val disowned = SourceEntry(
            id = "bri",
            name = "Bank BRI",
            icon = 0,
            source = object : EmailSource {
                override val query = EmailQuery.from("customercare@bri.co.id")
                override suspend fun parse(email: Email): List<ParsedTransaction> = emptyList()
            },
        )

        TransactionUpdater(
            sources = { listOf(disowned) },
            accountRepository = repos.accounts,
            accountSourceRepository = repos.accountSources,
            emailRepository = repos.emails,
            transactionRepository = repos.transactions,
            gmailApi = gmail,
            rules = CategoryRuleRepository(repos.db),
        ).reparseSources(setOf("bri"))

        // The row it wrote earlier is not deleted, nothing new is written, and
        // the record still says who looked — it is just that the look's answer
        // was "nothing". The next app update with a better source re-reads it.
        val stored = repos.transactions.all().single()
        assertEquals("BANK BRI", stored.merchant)
        val email = repos.emails.all().single()
        assertEquals(listOf("bri"), email.triedSourceIds)
        assertEquals("bri", email.parsedBySourceId)
    }
}
