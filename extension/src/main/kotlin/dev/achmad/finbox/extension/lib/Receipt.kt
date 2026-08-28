package dev.achmad.finbox.extension.lib

import dev.achmad.finbox.extension.Email
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.jsoup.Jsoup

/** A bank receipt, flattened to one line per row. */
class Receipt(val lines: List<String>) {

    /**
     * The value that follows one of [labels], on the same line or the next one.
     *
     * The next line is rejected when it is itself a label: an absent field would
     * otherwise take its neighbour's value.
     */
    fun field(vararg labels: String): String? {
        for ((index, line) in lines.withIndex()) {
            val label = labels.firstOrNull { line.startsWith(it, ignoreCase = true) } ?: continue
            val value = line.substring(label.length).trimStart(':', ' ', '\t').trim()
            if (value.isNotEmpty()) return value
            return lines.getOrNull(index + 1)
                ?.takeIf { next -> labels.none { next.startsWith(it, ignoreCase = true) } }
        }
        return null
    }

    /** The labelled amount, in minor units of the receipt's currency. */
    fun amount(vararg labels: String): Long? = field(*labels)?.let(::parseAmount)

    /**
     * The transaction's own timestamp, or null when the receipt states none.
     *
     * Some receipts state the time in the header and nowhere else, so any
     * parseable line will do.
     */
    fun date(vararg labels: String): Long? =
        field(*labels)?.let(::parseTimestamp) ?: lines.firstNotNullOfOrNull(::parseTimestamp)

    /**
     * The day and the clock arrive as two rows; the day is found by shape
     * because banks that do this head the section with a label of its own,
     * which is what [field] would answer with. Only the day's own line or below
     * is searched, so an unrelated time earlier in the mail cannot win, and the
     * zone is read with the clock.
     */
    fun splitDate(): Long? {
        val day = lines.indexOfFirst { parseTimestamp(it) != null }.takeIf { it >= 0 } ?: return null
        val clock = lines.drop(day).firstNotNullOfOrNull { CLOCK.find(it)?.value }.orEmpty()
        return parseTimestamp("${lines[day]} $clock")
    }

    /**
     * The first `Rp …` anywhere, in minor units, for receipts that state the
     * amount in prose.
     */
    fun statedAmount(): Long? = lines.firstNotNullOfOrNull(::findAmount)

    companion object {
        /**
         * Flattened here rather than upstream: a bank whose markup flattens
         * badly is a fix in this repo rather than a release of the app.
         */
        fun of(email: Email): Receipt = of(email.body)

        /** As [of], for a body already in hand. */
        fun of(body: String): Receipt =
            if (TAG.containsMatchIn(body)) ofHtml(body) else Receipt(body.toLines())

        /**
         * `Jsoup.text()` alone collapses the mail to one line, losing what pairs
         * a label with its value, so row markers are inserted and restored.
         */
        fun ofHtml(html: String): Receipt {
            val document = Jsoup.parse(html)
            document.outputSettings().prettyPrint(false)
            document.select("style, script, head").remove()
            // Literal marker, not a newline: text() collapses whitespace, so the break must survive as text.
            document.select("br, tr, p, div, li, h1, h2, h3, h4, table").before(MARKER)
            return Receipt(document.text().replace(MARKER, "\n").toLines())
        }

        private const val MARKER = "\\n"

        /** Enough markup to be worth parsing as html rather than reading as lines. */
        private val TAG = Regex("<[a-zA-Z!/]")

        private fun String.toLines(): List<String> =
            lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    }
}

/**
 * "Rp13.000", "Rp 2.000", "Rp1.000.000,00" as minor units — dots group, a comma
 * separates the sen.
 *
 * The sen are dropped, which for rupiah is both correct and free: ISO 4217
 * assigns IDR two minor digits but nothing has been priced in sen for decades,
 * so the app treats rupiah as having none and this returns the same number
 * either way. A currency that really does have cents needs the fraction kept,
 * and this function is where that starts.
 *
 * Indonesian grouping only.
 *
 * Lenient by design: the value was already found by its label, so whatever
 * digits are in it are the amount.
 */
fun parseAmount(raw: String): Long? {
    val digits = Regex("[\\d.,]+").find(raw)?.value ?: return null
    return digits.substringBeforeLast(',').replace(".", "").replace(",", "").toLongOrNull()
}

/**
 * The first amount in a line of prose.
 *
 * Unlike [parseAmount], this insists on a currency marker: an unlabelled line
 * is as likely to hold a card number or a terminal id as a price.
 */
fun findAmount(text: String): Long? =
    CURRENCY.find(text)?.groupValues?.get(1)?.let(::parseAmount)

/**
 * "11 Aug 2026, 10:30:27 WIB", "13 Agustus 2026 , 09:16:10", "03-Agu-2026".
 *
 * A stated zone wins — it is the same instant wherever the phone happens to
 * be — and [fallbackZone] covers the rest.
 */
fun parseTimestamp(text: String, fallbackZone: ZoneId = ZoneId.systemDefault()): Long? {
    val match = TIMESTAMP.find(text) ?: return null
    val (day, monthName, year, hour, minute, second) = match.destructured
    val month = MONTHS[monthName.lowercase().take(3)] ?: return null
    val zone = ZONE.find(text)?.let { ZONES[it.value.lowercase()] } ?: fallbackZone
    return try {
        LocalDateTime.of(
            year.toInt(),
            month,
            day.toInt(),
            hour.toIntOrNull() ?: 0,
            minute.toIntOrNull() ?: 0,
            second.toIntOrNull() ?: 0,
        ).atZone(zone).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null
    }
}

private val CURRENCY = Regex("(?:Rp|IDR)\\s*([\\d.,]+)", RegexOption.IGNORE_CASE)

/**
 * `11 Aug 2026, 10:30:27` — the time is optional, the seconds too, and a hyphen
 * groups the date as readily as a space ("03-Agu-2026").
 */
private val TIMESTAMP = Regex(
    "(\\d{1,2})[\\s-]+([A-Za-z]+)[\\s-]+(\\d{4})(?:\\s*,?\\s*(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?",
)

/** A clock, with the zone that qualifies it: "06:23:30 WIB", "10:53". */
private val CLOCK = Regex("\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*(?:WIB|WITA|WIT))?", RegexOption.IGNORE_CASE)

// Longest first: WITA would otherwise be read as WIT.
private val ZONE = Regex("\\b(WITA|WIB|WIT)\\b", RegexOption.IGNORE_CASE)

private val ZONES = mapOf(
    "wib" to ZoneOffset.ofHours(7),
    "wita" to ZoneOffset.ofHours(8),
    "wit" to ZoneOffset.ofHours(9),
)

/** Indonesian and English month names, by their first three letters. */
private val MONTHS = mapOf(
    "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4,
    "mei" to 5, "may" to 5, "jun" to 6, "jul" to 7,
    "agu" to 8, "aug" to 8, "sep" to 9, "okt" to 10, "oct" to 10,
    "nov" to 11, "des" to 12, "dec" to 12,
)
