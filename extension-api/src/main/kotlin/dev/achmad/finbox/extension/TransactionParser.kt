package dev.achmad.finbox.extension

/**
 * What a parser extension implements: the behaviour, and nothing else.
 *
 * Identity — `id`, `name`, `versionId` — comes from the `finbox { }` block in
 * Gradle. The build wraps this into the [TransactionSource] the app consumes,
 * so the same facts never have to be written in two places.
 *
 * Implement [SourceFactory] instead when one APK ships several parsers, since
 * those need an identity each and Gradle only describes one.
 */
interface TransactionParser {

    /** Whether this email belongs to this provider (sender, subject, format). */
    fun isEmailForProvider(email: EmailMessage): Boolean

    /** Convert the email into one or more standardized transactions. */
    suspend fun parseEmail(email: EmailMessage): List<ParsedTransaction>
}
