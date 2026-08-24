package dev.achmad.finbox.parser

/** Which way the money went — all the app itself knows. */
enum class TransactionDirection { INCOMING, OUTGOING }

/**
 * One type of transaction a parser can read: a QRIS payment, a top up, a
 * BI-Fast transfer.
 *
 * The app stores [key] and [direction] and only ever shows [name], so renaming
 * a key loses both the transactions filed under it and the user's switch.
 */
data class TransactionType(
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
 * [type] must be one of the parser's own [EmailParser.types]; the app drops a
 * transaction whose type the user switched off, and one it does not recognise.
 */
data class ParsedTransaction(
    /** Whole units of [currency], always positive — direction comes from [type]. */
    val amount: Long,
    /** ISO 4217, e.g. `IDR`. */
    val currency: String,
    /** Unix epoch millis. Pass [Email.date] when the receipt states no time. */
    val date: Long,
    val type: TransactionType,
    val merchant: String? = null,
    val description: String? = null,
    /**
     * The provider's own transaction reference. The app uses it for identity
     * when there is one, then falls back to the Gmail thread or message.
     */
    val reference: String? = null,
)
