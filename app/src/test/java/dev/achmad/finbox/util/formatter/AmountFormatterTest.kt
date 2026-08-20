package dev.achmad.finbox.util.formatter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountFormatterTest {

    @Test
    fun `amounts group in whole units, no decimals`() {
        // The symbol comes from CLDR, so only the number shape is asserted.
        assertTrue(formatAmount(7_456_000).contains("7.456.000"))
        assertTrue(formatAmount(-7_456_000).contains("7.456.000"))
        assertTrue(formatAmount(-7_456_000).startsWith("-"))
    }

    @Test
    fun `money in is signed too, and zero is not`() {
        assertTrue(formatAmount(7_456_000).startsWith("+"))
        assertTrue(formatAmount(0).startsWith("Rp"))
    }

    @Test
    fun `a missing amount is a dash, not a zero`() {
        assertEquals("-", formatAmount(null))
    }

    @Test
    fun `an unknown currency code falls back instead of throwing`() {
        assertTrue(formatAmount(1_000, "NOTACODE").contains("1.000"))
        assertTrue(formatAmount(1_000, "usd").contains("1.000"))
    }
}
