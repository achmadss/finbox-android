package dev.achmad.finbox.core.gmail

import dev.achmad.finbox.extension.EmailQuery
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailQueryTest {

    private fun millis(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(TimeZone.getDefault()).apply {
            clear()
            set(year, month - 1, day, 12, 0, 0)
        }.timeInMillis

    @Test
    fun `single sender needs no or-group`() {
        val query = buildGmailQuery(EmailQuery(from = listOf("bri.co.id")))

        assertEquals("from:bri.co.id", query)
    }

    @Test
    fun `terms in a field are or-ed, fields are and-ed`() {
        val query = buildGmailQuery(
            EmailQuery(
                from = listOf("noreply@bri.co.id", "bri@bri.co.id"),
                subject = listOf("transaksi"),
            ),
        )

        assertEquals("{from:noreply@bri.co.id from:bri@bri.co.id} subject:transaksi", query)
    }

    @Test
    fun `a term with a space is quoted`() {
        val query = buildGmailQuery(EmailQuery(subject = listOf("pemberitahuan transaksi")))

        assertEquals("subject:\"pemberitahuan transaksi\"", query)
    }

    @Test
    fun `extra terms pass through untouched`() {
        val query = buildGmailQuery(EmailQuery(from = listOf("bri.co.id"), extra = "-{promo diskon}"))

        assertEquals("from:bri.co.id -{promo diskon}", query)
    }

    @Test
    fun `the window is widened by a day at each end`() {
        // Gmail's date terms are whole local days and before: is exclusive, so a
        // narrower query would drop the emails at the very edges of the range.
        val query = buildGmailQuery(
            EmailQuery(from = listOf("bri.co.id")),
            after = millis(2026, 3, 1),
            before = millis(2026, 8, 31),
        )

        assertEquals("from:bri.co.id after:2026/02/28 before:2026/09/01", query)
    }

    @Test
    fun `no window means no date terms`() {
        val query = buildGmailQuery(EmailQuery(from = listOf("bri.co.id")))

        assertTrue("after:" !in query && "before:" !in query)
    }

    @Test
    fun `an empty query is reported as empty`() {
        assertTrue(EmailQuery().isEmpty)
        assertTrue(EmailQuery(extra = "   ").isEmpty)
        assertTrue(!EmailQuery(from = listOf("bri.co.id")).isEmpty)
    }
}
