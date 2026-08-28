package dev.achmad.data.model

/**
 * One fetched email, cached so an extension installed later re-reads it from here
 * instead of the provider: a re-parse then costs no fetch at all.
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
    /** Every extension that has looked, so an update only re-reads what is new to one. */
    val triedExtensionIds: List<String>,
    /** The extension that claimed it, null while unparsed. */
    val parsedByExtensionId: String?,
    val fetchedAt: Long,
) {
    val parsed: Boolean get() = parsedByExtensionId != null
}
