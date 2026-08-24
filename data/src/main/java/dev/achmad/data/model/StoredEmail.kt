package dev.achmad.data.model

/**
 * One fetched email, cached so a parser installed later re-reads it from here: a
 * Gmail fetch costs twenty quota units and nothing narrows a re-read, so paying
 * once is the difference between a re-parse being free and being an import.
 */
data class StoredEmail(
    /** With [accountId], the identity of an email. */
    val messageId: String,
    val threadId: String?,
    val accountId: String,
    val from: String,
    val subject: String,
    /** Unix epoch millis. */
    val date: Long,
    /** The body as the provider sent it, html where there is any. */
    val body: String?,
    /** Every parser that has looked, so an update only re-reads what is new to one. */
    val triedParserIds: List<Long>,
    /** The parser that claimed it, null while unparsed. */
    val parsedByParserId: Long?,
    val fetchedAt: Long,
) {
    val parsed: Boolean get() = parsedByParserId != null
}
