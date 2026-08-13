package dev.achmad.finbox.extension

enum class TransactionType { INCOME, EXPENSE, TRANSFER }

/**
 * A parsed transaction in a provider-neutral form. All fields are nullable: a
 * parser should return as much as it reliably extracted and leave the rest,
 * rather than guess; the app falls back quietly. `amount` is in whole units of
 * [currency] (e.g. rupiah for IDR).
 *
 * `reference` is the provider's own transaction reference, shown and exported
 * when there is one. It is not what identifies a transaction: the app keys
 * that on the email it was parsed from, so a provider that sends no reference
 * (Jago sends none at all) still imports and re-imports cleanly.
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
