package dev.achmad.finbox.extension

/**
 * One financial provider — BRI, Jago, Gojek — as an extension implements it.
 * A source confirms which emails are its own transaction mails and reads them.
 *
 * Fetching is the app's job: it syncs the mailbox and offers what arrives to
 * every installed source, so an extension never says which emails it wants.
 *
 * Identity (name, version) is not part of this: the app takes it from the APK
 * manifest, which the `finbox { }` block in Gradle fills in. Annotate the
 * implementation with [Source] to make it the APK's entry point.
 */
interface TransactionSource {

    /**
     * Whether this email is really one of this provider's transaction mails.
     * A bank sends statements, OTPs and promotions from the same address, and
     * those must not reach [parseEmail].
     */
    fun isEmailForProvider(email: EmailMessage): Boolean

    /** Convert the email into one or more standardized transactions. */
    suspend fun parseEmail(email: EmailMessage): List<ParsedTransaction>
}
