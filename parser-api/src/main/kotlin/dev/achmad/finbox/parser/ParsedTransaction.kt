package dev.achmad.finbox.parser

/** Which way the money went — all the app itself knows. */
enum class TransactionDirection { INCOMING, OUTGOING }

/**
 * One way money moved, as the provider names it: a QRIS payment, a top up, a
 * BI-Fast transfer. This is the provider's vocabulary, not the app's — the app
 * derives a category, and no parser ever sets one.
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
 * [method] must be one of the parser's own [EmailParser.methods].
 */
data class ParsedTransaction(
    /** Whole units of [currency], always positive — direction comes from [method]. */
    val amount: Long,
    /** ISO 4217, e.g. `IDR`. */
    val currency: String,
    /** Unix epoch millis. Pass [Email.date] when the receipt states no time. */
    val date: Long,
    val method: TransactionMethod,
    /**
     * Who the money went to or came from, as the provider names it: a shop, a
     * person, a wallet. Null when the receipt names none.
     */
    val merchant: String? = null,
    /**
     * What the provider said about *this particular* transaction — a transfer
     * note, a bill number, a reference someone typed. Never the email subject,
     * and never the kind of movement, which is [method].
     */
    val description: String? = null,
    /** The provider's own transaction reference, used for identity when present. */
    val reference: String? = null,
)
