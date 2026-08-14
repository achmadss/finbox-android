package dev.achmad.data.model

/**
 * One fetched email: what it takes to find it again, its body, and what has been
 * tried on it. Duplicate messages in a known thread are skipped before fetching.
 *
 * The body is kept so a parser installed or updated later re-reads it from here
 * — a Gmail fetch costs twenty quota units and nothing narrows a re-read, so
 * paying once is the difference between a re-parse being free and being an
 * import.
 *
 * [parsedBySourceId] is the source that claimed it; [triedSourceIds] is every
 * source that has looked, so an update only re-reads mail a new or updated
 * parser hasn't seen.
 */
data class Email(
    /** Gmail's message id — with [accountId], the identity of an email. */
    val messageId: String,
    /** Gmail's conversation id, shared by all messages in the thread. */
    val threadId: String?,
    val accountId: String,
    val from: String,
    val subject: String,
    /** When the email arrived, Unix epoch millis. */
    val date: Long,
    /** The html body as Gmail sent it. Null for mail fetched before bodies were kept. */
    val bodyHtml: String?,
    val triedSourceIds: List<Long>,
    val parsedBySourceId: Long?,
    val fetchedAt: Long,
) {
    val parsed: Boolean get() = parsedBySourceId != null
}
