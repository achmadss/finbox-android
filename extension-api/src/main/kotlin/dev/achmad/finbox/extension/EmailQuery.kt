package dev.achmad.finbox.extension

/**
 * How a source finds its own emails, instead of the app fetching everything
 * and asking every source about every message.
 *
 * The app turns this into a Gmail search and adds the time window the user
 * chose when importing — a source never specifies dates. Terms within a field
 * are OR-ed, fields are AND-ed:
 *
 * ```
 * EmailQuery(from = listOf("noreply@bri.co.id", "bri@bri.co.id"))
 * // {from:noreply@bri.co.id from:bri@bri.co.id} after:2026/02/01 before:2026/08/12
 * ```
 *
 * This narrows what gets downloaded; it does not decide what is a transaction.
 * A bank sends statements and promotions from the same address, so whatever
 * comes back still goes through [TransactionSource.isEmailForProvider].
 *
 * At least one field must be set — an empty query would download the whole
 * mailbox, so [ExtensionLoader] rejects a source that declares one.
 */
data class EmailQuery(
    /** Sender addresses or domains, e.g. `noreply@bri.co.id` or `bri.co.id`. */
    val from: List<String> = emptyList(),
    /** Subject fragments; quote-escaped by the app. */
    val subject: List<String> = emptyList(),
    /**
     * Raw Gmail search terms for anything the fields above don't cover, e.g.
     * `has:attachment` or `-{promo diskon}`. Passed through unchanged.
     */
    val extra: String? = null,
) {
    val isEmpty: Boolean
        get() = from.isEmpty() && subject.isEmpty() && extra.isNullOrBlank()
}
