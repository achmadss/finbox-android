package dev.achmad.finbox.util.formatter

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(ZoneId.systemDefault())

private val dateOnlyFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.systemDefault())

fun formatDate(epochMillis: Long?): String {
    if (epochMillis == null) return "-"
    return if (hasTime(epochMillis)) {
        dateFormatter.format(Instant.ofEpochMilli(epochMillis))
    } else {
        dateOnlyFormatter.format(Instant.ofEpochMilli(epochMillis))
    }
}

private fun hasTime(epochMillis: Long): Boolean {
    val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    return time.hour != 0 || time.minute != 0
}
