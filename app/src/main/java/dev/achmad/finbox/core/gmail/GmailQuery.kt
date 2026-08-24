package dev.achmad.finbox.core.gmail

import java.util.Calendar
import java.util.TimeZone

/**
 * The Gmail search for an initial import: the user's window, plus whatever the
 * account narrows by.
 *
 * Narrowing matters: listing ids costs 5 quota units per 500, while fetching
 * one message costs 20, so anything excluded here is the cheapest saving
 * available. What to exclude comes from the parsers (see [combineParserQueries]).
 *
 * Gmail's date terms are whole days in the local timezone and `before:` is
 * exclusive, so the range is widened by a day at each end and the exact bounds
 * are enforced later against each message's real timestamp.
 *
 * Everything null means the whole mailbox, capped by [GmailApi.listAllMessages].
 */
fun buildWindowQuery(
    after: Long? = null,
    before: Long? = null,
    narrow: String? = null,
): String = buildList {
    narrow?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
    after?.let { add("after:${gmailDate(it - DAY_MILLIS)}") }
    before?.let { add("before:${gmailDate(it + DAY_MILLIS)}") }
}.joinToString(" ")

/**
 * ORs what each parser asks for into one search.
 *
 * Null when any parser names no sender: that one wants the whole mailbox, and a
 * filter built from the others would silently skip the mail it came for.
 */
fun combineParserQueries(queries: List<String>): String? {
    val trimmed = queries.map { it.trim() }
    if (trimmed.isEmpty() || trimmed.any { it.isEmpty() }) return null
    return trimmed.distinct().joinToString(" OR ") { "($it)" }
}

private const val DAY_MILLIS = 24L * 60 * 60 * 1000

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
