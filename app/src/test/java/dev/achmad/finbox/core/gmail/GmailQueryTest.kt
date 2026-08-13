package dev.achmad.finbox.core.gmail

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
    fun `the window is widened by a day at each end`() {
        // Gmail's date terms are whole local days and before: is exclusive, so a
        // narrower query would drop the emails at the very edges of the range.
        val query = buildWindowQuery(
            after = millis(2026, 3, 1),
            before = millis(2026, 8, 31),
        )

        assertEquals("after:2026/02/28 before:2026/09/01", query)
    }

    @Test
    fun `one bound leaves the other open`() {
        assertEquals("after:2026/02/28", buildWindowQuery(after = millis(2026, 3, 1)))
        assertEquals("before:2026/09/01", buildWindowQuery(before = millis(2026, 8, 31)))
    }

    @Test
    fun `no window means an empty query`() {
        assertTrue(buildWindowQuery().isEmpty())
    }

    @Test
    fun `parser queries are ORed together, once each`() {
        val query = combineSourceQueries(
            listOf("from:BankBRI@bri.co.id", "from:noreply@jago.com", "from:BankBRI@bri.co.id"),
        )

        assertEquals("(from:BankBRI@bri.co.id) OR (from:noreply@jago.com)", query)
    }

    @Test
    fun `a source that names no sender turns narrowing off`() {
        // It wants the whole mailbox; filtering by the others would skip its mail.
        assertEquals(null, combineSourceQueries(listOf("from:BankBRI@bri.co.id", "  ")))
        assertEquals(null, combineSourceQueries(emptyList()))
    }
}
