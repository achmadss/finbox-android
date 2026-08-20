package dev.achmad.finbox.util.formatter

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private const val DEFAULT_CURRENCY = "IDR"

// ponytail: amounts are whole units, so no minor-unit scaling. Locale is Indonesian
// for the grouping style ("Rp7.456.000"); the currency symbol follows the code.
private val formatters = mutableMapOf<String, NumberFormat>()

/**
 * Money as text, e.g. `-Rp7.456.000`. Both directions carry their sign, so money in reads
 * as `+Rp7.456.000` against money out; zero stays unsigned. [currencyCode] is an ISO code
 * such as `IDR`; unknown or missing codes fall back to rupiah.
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
