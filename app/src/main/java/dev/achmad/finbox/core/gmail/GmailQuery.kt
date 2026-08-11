package dev.achmad.finbox.core.gmail

import dev.achmad.finbox.extension.EmailQuery
import java.util.Calendar
import java.util.TimeZone

/**
 * Turns a source's [EmailQuery] plus the user's import window into a Gmail
 * search string.
 *
 * Terms within a field are OR-ed with Gmail's `{a b}` syntax, fields are
 * AND-ed, and the window becomes `after:`/`before:`. Gmail's date terms are
 * whole days in the local timezone and `before:` is exclusive, so the range is
 * widened by a day at each end — the exact bounds are enforced later against
 * each message's real timestamp.
 */
fun buildGmailQuery(
    query: EmailQuery,
    after: Long? = null,
    before: Long? = null,
): String = buildList {
    if (query.from.isNotEmpty()) {
        add(orGroup(query.from.map { "from:${quoteIfNeeded(it)}" }))
    }
    if (query.subject.isNotEmpty()) {
        add(orGroup(query.subject.map { "subject:${quoteIfNeeded(it)}" }))
    }
    query.extra?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
    after?.let { add("after:${gmailDate(it - DAY_MILLIS)}") }
    before?.let { add("before:${gmailDate(it + DAY_MILLIS)}") }
}.joinToString(" ")

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

private fun orGroup(terms: List<String>): String =
    if (terms.size == 1) terms.single() else terms.joinToString(" ", prefix = "{", postfix = "}")

/** Gmail needs quotes around anything with a space; addresses never have one. */
private fun quoteIfNeeded(term: String): String =
    if (term.any { it.isWhitespace() }) "\"${term.replace("\"", "")}\"" else term

/** Gmail wants `yyyy/MM/dd` in the device's timezone. */
private fun gmailDate(epochMillis: Long): String {
    val calendar = Calendar.getInstance(TimeZone.getDefault())
    calendar.timeInMillis = epochMillis
    return "%04d/%02d/%02d".format(
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH),
    )
}
