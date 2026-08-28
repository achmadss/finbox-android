package dev.achmad.finbox.util.formatter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountFormatterTest {

    @Test
    fun `rupiah renders the number as it is stored, sen and all`() {
        // ISO 4217 gives IDR two fraction digits for a sen nothing has priced in
        // decades. Honouring that would render Rp7.456.000 as "Rp74.560,00", so
        // the override says zero and minor units and whole units coincide.
        // The symbol comes from CLDR, so only the digits are asserted.
        assertTrue(formatAmount(7_456_000, "IDR").contains("7.456.000"))
        assertTrue(formatAmount(-7_456_000, "IDR").contains("7.456.000"))
        assertTrue(formatAmount(-7_456_000, "IDR").startsWith("-"))
    }

    @Test
    fun `a currency with cents divides by them`() {
        // 1250 minor units of SGD is 12.50, which had no representation at all
        // while amounts were read as whole units.
        val text = formatAmount(1_250, "SGD")
        assertTrue(text, text.contains("12.50") || text.contains("12,50"))
    }

    @Test
    fun `money in is signed too, and zero is not`() {
        assertTrue(formatAmount(7_456_000, "IDR").startsWith("+"))
        assertTrue(formatAmount(0, "IDR").startsWith("Rp"))
    }

    @Test
    fun `a missing amount is a dash, not a zero`() {
        assertEquals("-", formatAmount(null))
    }

    @Test
    fun `an unrecognised currency keeps its code instead of becoming rupiah`() {
        val text = formatAmount(1_000, "NOTACODE")
        assertTrue(text, text.contains("NOTACODE"))
        assertTrue(text, !text.contains("Rp"))
    }

    @Test
    fun `a missing currency names no currency at all`() {
        val text = formatAmount(1_000)
        assertTrue(text, !text.contains("Rp"))
    }
}
