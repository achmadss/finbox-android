package dev.achmad.finbox.extension.lib.bni

import dev.achmad.finbox.extension.core.source.email.model.Email
import dev.achmad.finbox.extension.core.transaction.TransactionDirection
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real wondr by BNI notifications, flattened and redacted. */
class BniTest {

    private val extension = Bni()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/bni/$name.txt")) { "missing fixture $name" }
            .bufferedReader()
            .readText()

    private fun email(
        body: String,
        subject: String = "Transaksi berhasil!",
        from: String = "wondr by BNI <wondr@bni.co.id>",
        date: Long = 0L,
    ) = Email(
        messageId = "<test@bni.co.id>",
        threadId = "t1",
        subject = subject,
        from = from,
        date = date,
        body = body,
    )

    private fun claims(email: Email): Boolean =
        runBlocking { extension.parse(email) }.isNotEmpty()

    // The receipts state WIB, so the instant is fixed wherever the test runs.
    private fun wib(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZoneOffset.ofHours(7))
        .toInstant()
        .toEpochMilli()

    @Test
    fun `a transfer books the total, fee included, and its date is hyphenated`() {
        val parsed = runBlocking {
            extension.parse(email(fixture("transfer"), subject = "Transfer berhasil!"))
        }.single()

        // Rp20.000.000 sent, Rp2.500 admin: the total is what left the account.
        assertEquals(20_002_500L, parsed.amount)
        assertEquals("IDR", parsed.currency)
        assertEquals(TransactionDirection.OUTGOING, parsed.direction)
        assertEquals("NAMA PENERIMA", parsed.merchant)
        assertEquals(wib(2026, 8, 3, 6, 23, 30), parsed.date)
        assertEquals("20260803062319859497", parsed.reference)
    }

    @Test
    fun `a QRIS payment names the merchant and the method`() {
        val parsed = runBlocking { extension.parse(email(fixture("qris"))) }.single()

        assertEquals(23_500L, parsed.amount)
        assertEquals(TransactionDirection.OUTGOING, parsed.direction)
        assertEquals("QRIS INDOMARET", parsed.merchant)
        // "Jenis transaksi" already names the method, so duplicating it as a
        // description adds nothing.
        assertNull(parsed.description)
        assertEquals(wib(2026, 8, 11, 20, 43, 33), parsed.date)
        assertEquals("202608110843260325", parsed.reference)
    }

    @Test
    fun `a top up has a free fee and no recipient, and shortens the reference label`() {
        val parsed = runBlocking {
            extension.parse(email(fixture("topup"), subject = "Top-up berhasil!"))
        }.single()

        assertEquals(500_000L, parsed.amount)
        assertEquals(TransactionDirection.OUTGOING, parsed.direction)
        // The card being topped up is not a payee.
        assertNull(parsed.merchant)
        assertEquals(wib(2026, 8, 5, 10, 53, 18), parsed.date)
        assertEquals("2026080510531086751", parsed.reference)
    }

    @Test
    fun `every notification is claimed, and nothing else from the same sender is`() {
        assertTrue(claims(email(fixture("transfer"))))
        assertTrue(claims(email(fixture("qris"))))
        assertTrue(claims(email(fixture("topup"))))

        // A promotion states an amount, but no total and no reference id.
        assertFalse(
            claims(
                email("Kejar cashback Rp50.000 pakai wondr by BNI bulan ini!"),
            ),
        )
        assertFalse(claims(email("Kode OTP wondr kamu adalah 123456.")))
        assertFalse(claims(email(fixture("qris"), from = "promo@tokopedia.com")))
    }

    @Test
    fun `every fixture parses, and every one states a direction`() {
        val parsed = runBlocking {
            FIXTURES.flatMap { extension.parse(email(fixture(it.first), subject = it.second)) }
        }

        assertEquals(FIXTURES.size, parsed.size)
        // There is no method to check against a declared list any more. What is
        // left worth asserting is that a direction was decided at all, which the
        // type already guarantees — so this is really the fixtures still parsing.
        parsed.forEach { assertTrue("no direction", it.direction.name.isNotEmpty()) }
    }

    private companion object {
        /** Every fixture with the subject its mail actually carries. */
        val FIXTURES = listOf(
            "transfer" to "Transfer berhasil!",
            "qris" to "Transaksi berhasil!",
            "topup" to "Top-up berhasil!",
        )
    }
}
