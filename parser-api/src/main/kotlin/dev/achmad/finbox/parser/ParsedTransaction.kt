package dev.achmad.finbox.parser

/** Which way the money went — all the app itself knows. */
enum class TransactionDirection { INCOMING, OUTGOING }

/**
 * One way money moved, as the provider names it: a QRIS payment, a top up, a
 * BI-Fast transfer.
 *
 * This is the provider's vocabulary, not the app's. What the money was *for* is
 * a category, which the app derives and no parser ever sets.
 *
 * The app stores [key] and [direction] and only ever shows [name], so renaming
 * a key loses both the transactions filed under it and the user's switch.
 */
data class TransactionMethod(
    /** Stable id, e.g. `QRIS`. */
    val key: String,
    /** What the user sees, e.g. "QRIS Payment". */
    val name: String,
    val direction: TransactionDirection,
)

/**
 * A parsed transaction in a provider-neutral form.
 *
 * The required fields are the ones a transaction is worthless without: a parser
 * that cannot read them should return nothing rather than a hollow row. The
 * rest are optional because providers genuinely differ — some send no
 * reference, and a card purchase names no merchant.
 *
 * [method] must be one of the parser's own [EmailParser.methods]; the app drops
 * a transaction whose method the user switched off, and one it does not
 * recognise.
 */
data class ParsedTransaction(
    /** Whole units of [currency], always positive — direction comes from [method]. */
    val amount: Long,
    /** ISO 4217, e.g. `IDR`. */
    val currency: String,
    /** Unix epoch millis. Pass [Email.date] when the receipt states no time. */
    val date: Long,
    val method: TransactionMethod,
    val merchant: String? = null,
    val description: String? = null,
    /**
     * The provider's own transaction reference. The app uses it for identity
     * when there is one, then falls back to the Gmail thread or message.
     */
    val reference: String? = null,
)
