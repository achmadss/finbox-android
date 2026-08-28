package dev.achmad.finbox.extension.lib.mandiri

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

/** Real Livin' notifications, flattened and redacted. */
class MandiriTest {

    private val extension = Mandiri()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/mandiri/$name.txt")) { "missing fixture $name" }
            .bufferedReader()
            .readText()

    private fun email(
        body: String,
        subject: String = "Pembayaran Berhasil!",
        from: String = "Livin' <noreply.livin@bankmandiri.co.id>",
        date: Long = 0L,
    ) = Email(
        messageId = "<test@bankmandiri.co.id>",
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
    fun `a QR payment names the merchant, and the cents are dropped`() {
        val parsed = runBlocking { extension.parse(email(fixture("qris"))) }.single()

        // "Nominal Transaksi Rp 41.000,00" — a ledger in rupiah has no cents.
        assertEquals(41_000L, parsed.amount)
        assertEquals("IDR", parsed.currency)
        assertEquals(TransactionDirection.OUTGOING, parsed.direction)
        assertEquals("APOTEK KAWI JAYA BSD", parsed.merchant)
        assertEquals(wib(2026, 7, 27, 15, 22, 45), parsed.date)
        // The QRIS reference on the line below must not win.
        assertEquals("2607271121582462322", parsed.reference)
    }

    @Test
    fun `a top up names the provider and labels its nominal differently`() {
        val parsed = runBlocking {
            extension.parse(email(fixture("topup"), subject = "Top-up e-money Berhasil"))
        }.single()

        assertEquals(200_000L, parsed.amount)
        assertEquals(TransactionDirection.OUTGOING, parsed.direction)
        assertEquals("e-money", parsed.merchant)
        assertEquals(wib(2026, 7, 28, 7, 57, 27), parsed.date)
        assertEquals("702607280757221223", parsed.reference)
    }

    @Test
    fun `an SBN order states neither date nor reference, so the mail's own stand in`() {
        val arrived = wib(2026, 8, 14, 9, 0, 0)
        val parsed = runBlocking {
            extension.parse(
                email(fixture("sbn"), subject = "Pemesanan SBN Berhasil!", date = arrived),
            )
        }.single()

        assertEquals(100_000_000L, parsed.amount)
        assertEquals(TransactionDirection.OUTGOING, parsed.direction)
        assertEquals(arrived, parsed.date)
        assertNull(parsed.merchant)
        assertNull(parsed.reference)
    }

    @Test
    fun `every notification is claimed, and nothing else from the same sender is`() {
        assertTrue(claims(email(fixture("qris"))))
        assertTrue(claims(email(fixture("topup"))))
        assertTrue(claims(email(fixture("sbn"))))

        // A promotion states an amount in prose, never as a labelled nominal.
        assertFalse(
            claims(
                email("Dapatkan diskon Rp50.000 untuk pembayaran di Livin' Sukha!"),
            ),
        )
        assertFalse(claims(email("Kode OTP Livin' kamu adalah 123456.")))
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
            "qris" to "Pembayaran Berhasil!",
            "topup" to "Top-up e-money Berhasil",
            "sbn" to "Pemesanan SBN Berhasil!",
        )
    }
}
