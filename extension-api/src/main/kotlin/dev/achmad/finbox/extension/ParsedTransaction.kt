package dev.achmad.finbox.extension

/** Which way the money went — all the app itself knows. */
enum class TransactionDirection { INCOMING, OUTGOING }

/**
 * A parsed transaction in a provider-neutral form.
 *
 * There is no `method`. What a bank calls a movement was a fact about one
 * bank's email template and mostly not even that — some were invented from a
 * subject line — so anything keyed on it looked conditioned on real information
 * when it was not. [direction] was the one part worth keeping, and it is a
 * field of its own now rather than something reached through a method that
 * usually did not exist.
 */
data class ParsedTransaction(
    /**
     * Minor units of [currency] — cents, sen — always positive.
     *
     * Minor rather than whole so a currency with fractional units can be
     * represented at all. IDR has none, so a rupiah amount is the same number
     * either way; SGD 12.50 is 1250 and has no other honest form.
     */
    val amount: Long,
    /** ISO 4217, e.g. `IDR`. */
    val currency: String,
    /** Unix epoch millis. Pass [Email.date] when the receipt states no time. */
    val date: Long,
    val direction: TransactionDirection,
    /**
     * Who the money went to or came from, as the provider names it: a shop, a
     * person, a wallet. Null when the receipt names none.
     */
    val merchant: String? = null,
    /**
     * What the provider said about *this particular* transaction — a transfer
     * note, a bill number, a reference someone typed. Never the email subject,
     * and never the kind of movement, which is not recorded at all.
     */
    val description: String? = null,
    /** The provider's own transaction reference, used for identity when present. */
    val reference: String? = null,
)
