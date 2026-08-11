package dev.achmad.finbox.extension

/**
 * What a parser extension implements, and the whole of the extension API.
 *
 * Identity — name and version — is not part of it: the app reads those from
 * the APK manifest, which the `finbox { }` block in Gradle fills in. Annotate
 * the implementation with [Parser] to make it the APK's entry point.
 */
interface TransactionParser {

    /** Whether this email belongs to this provider (sender, subject, format). */
    fun isEmailForProvider(email: EmailMessage): Boolean

    /** Convert the email into one or more standardized transactions. */
    suspend fun parseEmail(email: EmailMessage): List<ParsedTransaction>
}
