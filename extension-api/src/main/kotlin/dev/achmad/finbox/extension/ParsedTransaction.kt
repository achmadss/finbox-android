package dev.achmad.finbox.extension

enum class TransactionType { INCOME, EXPENSE, TRANSFER }

/**
 * A parsed transaction in a provider-neutral form. All fields are nullable: a
 * parser should return as much as it reliably extracted and leave the rest,
 * rather than guess; the app falls back quietly. `amount` is in whole units of
 * [currency] (e.g. rupiah for IDR).
 *
 * `reference` is the provider's own transaction reference, shown and exported
 * when there is one. The app uses it for identity when present, then falls back
 * to the Gmail thread or message it was parsed from, so a provider that sends
 * no reference (Jago sends none at all) still imports cleanly.
 */
data class ParsedTransaction(
    val date: Long?,
    val amount: Long?,
    val currency: String?,
    val type: TransactionType?,
    val merchant: String?,
    val description: String?,
    val reference: String?,
)
