package dev.achmad.finbox.core.util

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatAmount(amount: Long?, currency: String?): String {
    if (amount == null) return "-"
    val symbol = when (currency) {
        "IDR" -> "Rp"
        "USD" -> "$"
        "EUR" -> "€"
        else -> "$currency "
    }
    val formatted = NumberFormat.getNumberInstance(Locale.US).format(amount)
    return "$symbol$formatted"
}

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
