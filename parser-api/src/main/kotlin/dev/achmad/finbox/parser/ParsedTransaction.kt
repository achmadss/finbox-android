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
    /**
     * Stable id, e.g. `QRIS`.
     *
     * Most receipts state this outright — a "transaction kind" line, or
     * whatever the provider calls it. That line belongs here and nowhere else.
     */
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
    /**
     * Who the money went to or came from, as the provider names it.
     *
     * A shop, a person, a wallet, a card — whatever the receipt calls the other
     * side. Do not judge it: a top up to an e-wallet is a real counterparty
     * even though it says nothing about what was later bought. The app decides
     * what a name is worth; the parser only reports it.
     *
     * Null when the receipt names none. That is a fact worth reporting, and
     * inventing something to fill the gap is worse than the gap.
     */
    val merchant: String? = null,
    /**
     * What the provider said about *this particular* transaction.
     *
     * A transfer note, a bill number, a reference someone typed. The test is
     * whether two transactions of the same kind could carry different text
     * here. If every receipt of a kind says the same thing, it is not a
     * description of anything.
     *
     * Never the email subject, and never the provider's name for the kind of
     * movement — that is [method], and duplicating it here buys nothing.
     *
     * This matters more than it looks. The app treats merchant and description
     * as its evidence that a transaction's purpose is knowable at all, so
     * boilerplate here reads as information and turns "nothing to go on" into
     * a guess.
     */
    val description: String? = null,
    /**
     * The provider's own transaction reference. The app uses it for identity
     * when there is one, then falls back to the Gmail thread or message.
     */
    val reference: String? = null,
)
