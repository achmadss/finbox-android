package dev.achmad.data.model

/**
 * One synced email: what it takes to find it again and what has been tried on
 * it. The body is not kept — a parser installed later re-reads it from Gmail.
 *
 * [parsedBySourceId] is the source that claimed it; [triedSourceIds] is every
 * source that has looked, so an update only re-reads mail a new or updated
 * parser hasn't seen.
 */
data class Email(
    /** Gmail's message id — with [accountId], the identity of an email. */
    val messageId: String,
    val accountId: String,
    val from: String,
    val subject: String,
    /** When the email arrived, Unix epoch millis. */
    val date: Long,
    val triedSourceIds: List<Long>,
    val parsedBySourceId: Long?,
    val fetchedAt: Long,
) {
    val parsed: Boolean get() = parsedBySourceId != null
}
