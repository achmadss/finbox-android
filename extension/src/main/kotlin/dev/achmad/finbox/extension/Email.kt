package dev.achmad.finbox.extension

/** One email, as the app hands it to an extension. */
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
