package dev.achmad.finbox.util.formatter

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private const val DEFAULT_CURRENCY = "IDR"

// Whole units, so no minor-unit scaling; id-ID gives the Indonesian grouping style.
private val formatters = mutableMapOf<String, NumberFormat>()

/**
 * Money as text, e.g. `-Rp7.456.000`. Positives get a `+` so money in reads
 * against money out; zero stays unsigned. Unknown or missing [currencyCode]s
 * fall back to rupiah.
 */
fun formatAmount(amount: Long?, currencyCode: String? = null): String {
    if (amount == null) return "-"
    val code = currencyCode?.uppercase(Locale.ROOT) ?: DEFAULT_CURRENCY
    val formatter = formatters.getOrPut(code) {
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID")).apply {
            maximumFractionDigits = 0
            runCatching { currency = Currency.getInstance(code) }
        }
    }
    val text = formatter.format(amount)
    return if (amount > 0) "+$text" else text
}
