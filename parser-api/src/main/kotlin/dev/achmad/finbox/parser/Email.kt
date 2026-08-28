package dev.achmad.finbox.parser

/** One email, as the app hands it to a parser. */
data class Email(
    val messageId: String,
    /** Empty when Gmail returned none. */
    val threadId: String,
    val subject: String,
    val from: String,
    /** Unix epoch millis. */
    val date: Long,
    /** As the provider sent it, html where there is any. */
    val body: String,
)
