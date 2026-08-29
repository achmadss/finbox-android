package dev.achmad.finbox.core.update.transaction

import dev.achmad.data.model.StoredEmail
import dev.achmad.data.model.Transaction
import dev.achmad.finbox.source.core.ParsedTransaction
import dev.achmad.finbox.source.core.Source
import dev.achmad.finbox.source.core.SourceEntry
import dev.achmad.finbox.source.core.TransactionDirection
import dev.achmad.finbox.source.core.email.Email
import dev.achmad.finbox.source.core.email.EmailQuery
import dev.achmad.finbox.source.core.email.EmailSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole of the "mail nothing recognises is dropped quietly" decision,
 * which lives in [parseEmail] rather than in the repository.
 *
 * The repository half is covered in `data` (an empty upsert writes nothing);
 * this is the half where the empty list comes from — a source that yields
 * nothing, a source that fails, and the record that remembers who looked.
 */
class TransactionParseTest {

    private val now = 1_700_000_000_000L

    private fun source(
        id: String,
        onParse: (Email) -> List<ParsedTransaction>,
    ): SourceEntry = SourceEntry(
        id = id,
        name = id,
        icon = 0,
        source = object : EmailSource {
            override val query = EmailQuery.from("$id@bank.id")
            override suspend fun parse(email: Email) = onParse(email)
        },
    )

    private fun stored(tried: List<String> = emptyList()) = StoredEmail(
        messageId = "message-1",
        threadId = null,
        accountId = "account",
        from = "receipt@bank.id",
        subject = "Receipt",
        date = now,
        body = "<html>receipt</html>",
        triedSourceIds = tried,
        parsedBySourceId = null,
        fetchedAt = now,
    )

    private fun message() = Email(
        messageId = "message-1",
        threadId = "",
        subject = "Receipt",
        from = "receipt@bank.id",
        date = now,
        body = "<html>receipt</html>",
    )

    private fun parsed(
        amount: Long = 25_000L,
        merchant: String? = "Kopi Kenangan",
    ) = ParsedTransaction(
        amount = amount,
        currency = "IDR",
        date = now,
        direction = TransactionDirection.OUTGOING,
        merchant = merchant,
    )

    @Test
    fun `mail nothing recognises is dropped quietly`() = runBlocking {
        val result = parseEmail(stored(), message(), listOf(source("bri") { emptyList() }), now = now)

        assertTrue(result.transactions.isEmpty())
        // Not a failure, not an accident: the record says who looked and that
        // nobody claimed it, so only a new source tries again.
        assertEquals(listOf("bri"), result.email.triedSourceIds)
        assertNull(result.email.parsedBySourceId)
    }

    @Test
    fun `a failing source is a disown, not a failure`() = runBlocking {
        var failingCalled = false
        val failing = source("bri") {
            failingCalled = true
            error("template changed")
        }
        val bni = source("bni") { listOf(parsed(merchant = "Bank BNI")) }

        val result = parseEmail(stored(), message(), listOf(failing, bni), now = now)

        assertTrue(failingCalled)
        assertEquals("bni", result.email.parsedBySourceId)
        assertEquals("Bank BNI", result.transactions.single().merchant)
        assertEquals(listOf("bri", "bni"), result.email.triedSourceIds)
    }

    @Test
    fun `first claim wins and no later source is consulted`() = runBlocking {
        var laterCalled = false
        val first = source("bri") { listOf(parsed(amount = 25_000L)) }
        val later = source("bni") {
            laterCalled = true
            listOf(parsed(amount = 99_999L))
        }

        val result = parseEmail(stored(), message(), listOf(first, later), now = now)

        assertEquals(25_000L, result.transactions.single().amount)
        assertEquals("bri", result.email.parsedBySourceId)
        assertTrue(!laterCalled)
    }

    @Test
    fun `a source that already looked is not consulted again`() = runBlocking {
        var calls = 0
        val bri = source("bri") {
            calls++
            listOf(parsed())
        }

        parseEmail(stored(tried = listOf("bri")), message(), listOf(bri), now = now)

        assertEquals(0, calls)
    }

    @Test
    fun `force consults even a source that already looked`() = runBlocking {
        var calls = 0
        val bri = source("bri") {
            calls++
            listOf(parsed())
        }

        val result = parseEmail(stored(tried = listOf("bri")), message(), listOf(bri), force = true, now = now)

        assertEquals(1, calls)
        assertEquals("bri", result.email.parsedBySourceId)
    }

    @Test
    fun `a source that reads something else is skipped without stopping the rest`() = runBlocking {
        val bni = source("bni") { listOf(parsed()) }
        val other = SourceEntry(
            id = "pdf",
            name = "pdf",
            icon = 0,
            source = object : Source {},
        )

        val result = parseEmail(stored(), message(), listOf(other, bni), now = now)

        assertEquals("bni", result.email.parsedBySourceId)
        assertEquals(listOf("pdf", "bni"), result.email.triedSourceIds)
    }

    @Test
    fun `a source can disown one email and claim the next`() = runBlocking {
        val bri = source("bri") { email ->
            if (email.messageId == "message-2") listOf(parsed(merchant = "Lain")) else emptyList()
        }

        val first = parseEmail(stored(), message(), listOf(bri), now = now)
        val second = parseEmail(
            stored(),
            Email(
                messageId = "message-2",
                threadId = "",
                subject = "Receipt",
                from = "receipt@bank.id",
                date = now,
                body = "<html>receipt</html>",
            ),
            listOf(bri),
            now = now,
        )

        assertTrue(first.transactions.isEmpty())
        assertNull(first.email.parsedBySourceId)
        assertEquals("Lain", second.transactions.single().merchant)
        assertEquals("bri", second.email.parsedBySourceId)
    }

    @Test
    fun `an import never assigns a category`() = runBlocking {
        val bri = source("bri") { listOf(parsed()) }

        val result = parseEmail(stored(), message(), listOf(bri), now = now)

        for (transaction: Transaction in result.transactions) {
            assertNull(transaction.categoryName)
            assertNull(transaction.categorySource)
            assertNull(transaction.editedAt)
        }
    }
}
