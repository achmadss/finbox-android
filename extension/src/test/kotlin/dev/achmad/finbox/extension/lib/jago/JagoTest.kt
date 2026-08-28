package dev.achmad.finbox.extension.lib.jago

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

/** Real Jago notifications, flattened and redacted. */
class JagoTest {

    private val extension = Jago()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/jago/$name.txt")) { "missing fixture $name" }
            .bufferedReader()
            .readText()

    private fun email(
        body: String,
        subject: String = "You have made a payment to BEl Shop",
        from: String = "Jago <noreply@jago.com>",
        date: Long = 0L,
    ) = Email(
        messageId = "<test@jago.com>",
        threadId = "t1",
        subject = subject,
        from = from,
        date = date,
        body = body,
    )

    private fun claims(email: Email): Boolean =
        runBlocking { extension.parse(email) }.isNotEmpty()

    // The receipts state WIB, so the instant is fixed wherever the test runs.
    private fun millis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.ofHours(7))
        .toInstant()
        .toEpochMilli()

    @Test
    fun `a payment is an expense, with the merchant it went to`() {
        val parsed = runBlocking { extension.parse(email(fixture("payment"))) }.single()

        assertEquals(2_000L, parsed.amount)
        assertEquals("IDR", parsed.currency)
        assertEquals(TransactionDirection.OUTGOING, parsed.direction)
        assertEquals("BEl Shop", parsed.merchant)
        assertEquals(millis(2026, 8, 5, 13, 23), parsed.date)
        // Jago sends no reference number of any method.
        assertNull(parsed.reference)
    }

    @Test
    fun `a transfer names the recipient, and the date has no comma`() {
        val parsed = runBlocking {
            extension.parse(
                email(fixture("transfer"), subject = "You have made a transfer"),
            )
        }.single()

        assertEquals(34_000L, parsed.amount)
        assertEquals(TransactionDirection.OUTGOING, parsed.direction)
        assertEquals("NAMA PENERIMA", parsed.merchant)
        assertEquals(millis(2026, 7, 22, 13, 53), parsed.date)
    }

    @Test
    fun `a partner transaction labels with colons and names the partner`() {
        val parsed = runBlocking {
            extension.parse(
                email(fixture("partner"), subject = "You have made a transaction via GoPay"),
            )
        }.single()

        assertEquals(41_100L, parsed.amount)
        assertEquals(TransactionDirection.OUTGOING, parsed.direction)
        assertEquals("GoPay", parsed.merchant)
        assertEquals(millis(2026, 8, 11, 19, 56), parsed.date)
    }

    @Test
    fun `a debit card purchase states only its amount, so the mail's date is used`() {
        val arrived = millis(2026, 8, 6, 2, 49)
        val parsed = runBlocking {
            extension.parse(
                email(
                    fixture("debitcard"),
                    subject = "You have made a transaction using your debit card",
                    date = arrived,
                ),
            )
        }.single()

        assertEquals(33_189L, parsed.amount)
        assertEquals(TransactionDirection.OUTGOING, parsed.direction)
        assertEquals(arrived, parsed.date)
        // Nothing but an amount: no summary means no merchant to name.
        assertNull(parsed.merchant)
    }

    @Test
    fun `investment pocket movements are not spending, so they are dropped`() {
        // The buy has a full summary and would otherwise parse as a payment.
        assertTrue(runBlocking { extension.parse(email(fixture("stockbuy"))) }.isEmpty())
        assertTrue(runBlocking { extension.parse(email(fixture("stocksell"))) }.isEmpty())

        assertFalse(claims(email(fixture("stockbuy"))))
        assertFalse(claims(email(fixture("stocksell"))))
    }

    @Test
    fun `every notification is claimed, and nothing else from the same sender is`() {
        assertTrue(claims(email(fixture("payment"))))
        assertTrue(claims(email(fixture("transfer"))))
        assertTrue(claims(email(fixture("partner"))))
        assertTrue(claims(email(fixture("debitcard"))))

        // A promotion states an amount too, but no summary and no card.
        assertFalse(
            claims(
                email("Get Rp50.000 cashback when you pay with Jago this month!"),
            ),
        )
        assertFalse(claims(email("Your Jago OTP is 123456.")))
        assertFalse(
            claims(email(fixture("payment"), from = "promo@tokopedia.com")),
        )
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
            "payment" to "You have made a payment to BEl Shop",
            "transfer" to "You have made a transfer",
            "partner" to "You have made a transaction via GoPay",
            "debitcard" to "You have made a transaction using your debit card",
        )
    }
}
