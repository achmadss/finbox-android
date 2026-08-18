package dev.achmad.finbox.parser

/**
 * Which way the money went. The app knows only these two — everything a
 * provider calls a transaction is one or the other, and the provider's own
 * vocabulary lives in [TransactionKind].
 */
enum class TransactionType { INCOME, EXPENSE }

/**
 * One kind of transaction a source can read: a QRIS payment, a top up, a
 * BI-Fast transfer.
 *
 * A source declares these up front ([TransactionSource.kinds]) so the app can
 * list them and let the user switch individual ones off, and tags each parsed
 * transaction with the one it came from. The app stores [key] and [type]; [name]
 * is only ever shown.
 */
data class TransactionKind(
    /**
     * Stable id, e.g. `QRIS`. Stored with every transaction parsed under it and
     * with the user's on/off choice, so renaming one loses both — rename [name]
     * instead, which is what the user actually reads.
     */
    val key: String,
    /** What the user sees in the parser's kind list, e.g. "QRIS Payment". */
    val name: String,
    /** Whether money came in or went out. */
    val type: TransactionType,
)

/**
 * A parsed transaction in a provider-neutral form. All fields are nullable: a
 * parser should return as much as it reliably extracted and leave the rest,
 * rather than guess; the app falls back quietly. `amount` is in whole units of
 * [currency] (e.g. rupiah for IDR).
 *
 * `kind` must be one of the source's own [TransactionSource.kinds] — the app
 * drops a transaction whose kind the user has switched off, and one it does not
 * recognise. A source that can't tell what it read should declare a catch-all
 * kind and use that, so the transaction stays visible and switchable rather
 * than vanishing.
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
    val kind: TransactionKind?,
    val merchant: String?,
    val description: String?,
    val reference: String?,
)
