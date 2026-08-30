package dev.achmad.finbox.core.gmail

import java.util.Calendar
import java.util.TimeZone

/**
 * The Gmail search for an initial import: the user's window, plus whatever the
 * account narrows by.
 *
 * Gmail's date terms are whole days in the local timezone, so the range is
 * widened by a day at each end; exact bounds are enforced later against each
 * message's real timestamp. All nulls mean the whole mailbox.
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
 * ORs what each source asks for into one search. Null when any source names no
 * sender: its mail would be excluded by the others' filters.
 */
fun combineSourceQueries(queries: List<String>): String? {
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
