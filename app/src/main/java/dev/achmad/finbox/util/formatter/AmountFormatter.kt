package dev.achmad.finbox.util.formatter

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Money as text, e.g. `-Rp7.456.000`, from an amount in minor units. Positives
 * get a `+` so money in reads against money out; zero stays unsigned.
 *
 * A currency this device does not know is rendered as the number and the code —
 * `1000 XYZ` — with no symbol and no grouping borrowed from somewhere else.
 * Rendering it as rupiah, which is what this used to do, silently mislabels
 * foreign money as local money, and missing data is its own answer.
 */
fun formatAmount(amount: Long?, currencyCode: String? = null): String {
    if (amount == null) return "-"
    val code = currencyCode?.uppercase(Locale.ROOT)
    val currency = code?.let { runCatching { Currency.getInstance(it) }.getOrNull() }
        ?: return unknownCurrency(amount, code)
    val formatter = formatters.getOrPut(currency.currencyCode) {
        // The currency's own locale, not Indonesia's: grouping and symbol
        // placement are properties of the money, not of this app's first user.
        NumberFormat.getCurrencyInstance(localeFor(currency)).apply {
            val digits = fractionDigitsOf(currency)
            minimumFractionDigits = digits
            maximumFractionDigits = digits
            this.currency = currency
        }
    }
    val text = formatter.format(toMajorUnits(amount, currency))
    return if (amount > 0) "+$text" else text
}

private val formatters = mutableMapOf<String, NumberFormat>()

/**
 * How many minor units make a major one.
 *
 * ISO 4217 assigns rupiah two, for the sen — a unit that has not bought
 * anything in decades and that no Indonesian receipt, bank or price tag
 * writes. Taking Java's answer would make every stored rupiah amount mean one
 * hundredth of what the receipt said, and render Rp1.000 as "Rp10,00".
 *
 * So the currency answers, except where its minor unit is defunct in practice
 * and the override says otherwise.
 *
 * ponytail: one entry, because one currency is supported. A second country
 * whose minor unit is dead — VND, and the zero-decimal list is short and
 * well known — adds a line here. If it ever grows past a handful, take the
 * display digits from CLDR instead of listing them.
 */
private val DEFUNCT_MINOR_UNITS = mapOf("IDR" to 0)

private fun fractionDigitsOf(currency: Currency): Int =
    DEFUNCT_MINOR_UNITS[currency.currencyCode]
        ?: currency.defaultFractionDigits.coerceAtLeast(0)

/**
 * Minor units as the major-unit number the formatter wants.
 *
 * Long division would drop the cents it is being asked to render, so this goes
 * through Double for the currencies that have any. Amounts are receipt-sized,
 * far inside the range a Double represents exactly.
 */
private fun toMajorUnits(amount: Long, currency: Currency): Number {
    val digits = fractionDigitsOf(currency)
    if (digits <= 0) return amount
    var divisor = 1L
    repeat(digits) { divisor *= 10 }
    return amount.toDouble() / divisor
}

/** The first locale that spends this currency, or the device's own. */
private fun localeFor(currency: Currency): Locale =
    localeCache.getOrPut(currency.currencyCode) {
        Locale.getAvailableLocales()
            .firstOrNull { locale ->
                runCatching { Currency.getInstance(locale) }.getOrNull() == currency
            }
            ?: Locale.getDefault()
    }

private val localeCache = mutableMapOf<String, Locale>()

/**
 * A currency nothing here recognises: the digits, then whatever it was called.
 *
 * No symbol is invented and no fraction is assumed, because there is nothing to
 * base either on.
 */
private fun unknownCurrency(amount: Long, code: String?): String {
    val digits = NumberFormat.getIntegerInstance(Locale.getDefault()).format(amount)
    val text = if (code.isNullOrBlank()) digits else "$digits $code"
    return if (amount > 0) "+$text" else text
}
