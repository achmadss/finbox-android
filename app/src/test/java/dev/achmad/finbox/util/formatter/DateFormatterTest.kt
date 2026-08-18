package dev.achmad.finbox.util.formatter

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DateFormatterTest {

    private val afternoon = LocalDateTime.of(2026, 8, 18, 14, 5)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    @Test
    fun `time of day follows the clock setting`() {
        assertEquals("14:05", formatTime(afternoon, use24Hour = true))
        // The suffix is the JVM locale's, so only the clock part is asserted.
        assertTrue(formatTime(afternoon, use24Hour = false).startsWith("2:05"))
    }

    @Test
    fun `a date with a time follows it too`() {
        assertEquals("18 Aug 2026 14:05", formatDate(afternoon, use24Hour = true))
        assertTrue(formatDate(afternoon, use24Hour = false).startsWith("18 Aug 2026 2:05"))
    }
}
