package dev.achmad.finbox.util.formatter

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.systemDefault())

private val dateFormatter12: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy h:mm a").withZone(ZoneId.systemDefault())

private val dateOnlyFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.systemDefault())

private val dayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM").withZone(ZoneId.systemDefault())

private val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

private val timeFormatter12: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a").withZone(ZoneId.systemDefault())

private val monthYearFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy")

private val monthNameFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM")

/** [use24Hour] comes from the clock setting — see `rememberUse24HourClock`. */
fun formatDate(epochMillis: Long?, use24Hour: Boolean): String {
    if (epochMillis == null) return "-"
    return if (hasTime(epochMillis)) {
        val formatter = if (use24Hour) dateFormatter else dateFormatter12
        formatter.format(Instant.ofEpochMilli(epochMillis))
    } else {
        dateOnlyFormatter.format(Instant.ofEpochMilli(epochMillis))
    }
}

/** The calendar day, e.g. `10 Aug 2026`. */
fun formatDateOnly(epochMillis: Long): String =
    dateOnlyFormatter.format(Instant.ofEpochMilli(epochMillis))

/** Day and month, e.g. `10 Aug`. */
fun formatDay(epochMillis: Long?): String {
    if (epochMillis == null) return "-"
    return dayFormatter.format(Instant.ofEpochMilli(epochMillis))
}

fun formatDay(date: LocalDate): String = dayFormatter.format(date)

fun formatTime(epochMillis: Long?, use24Hour: Boolean): String {
    if (epochMillis == null) return "-"
    val formatter = if (use24Hour) timeFormatter else timeFormatter12
    return formatter.format(Instant.ofEpochMilli(epochMillis))
}

fun formatMonthYear(yearMonth: YearMonth): String = monthYearFormatter.format(yearMonth)

/** Month alone, e.g. `August` — for a neighbouring month whose year is already on screen. */
fun formatMonthName(yearMonth: YearMonth): String = monthNameFormatter.format(yearMonth)

/** The calendar day [epochMillis] falls on, in the device's zone. */
fun toLocalDate(epochMillis: Long): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

private fun hasTime(epochMillis: Long): Boolean {
    val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    return time.hour != 0 || time.minute != 0
}
