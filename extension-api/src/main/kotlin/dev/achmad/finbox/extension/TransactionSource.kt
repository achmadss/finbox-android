package dev.achmad.finbox.extension

/**
 * One financial provider — BRI, Jago, Gojek — as an extension implements it.
 * A source says which emails to fetch, confirms the ones that are actually
 * transactions, and reads them.
 *
 * Identity (name, version) is not part of this: the app takes it from the APK
 * manifest, which the `finbox { }` block in Gradle fills in. Annotate the
 * implementation with [Source] to make it the APK's entry point.
 */
interface TransactionSource {

    /**
     * Which emails the app should fetch for this source. Narrow it as much as
     * the provider allows — everything matched here is downloaded.
     */
    val emailQuery: EmailQuery

    /**
     * Whether this email is really one of this provider's transaction mails.
     * [emailQuery] only narrows the download; a bank sends statements, OTPs and
     * promotions from the same address, and those must not reach [parseEmail].
     */
    fun isEmailForProvider(email: EmailMessage): Boolean

    /** Convert the email into one or more standardized transactions. */
    suspend fun parseEmail(email: EmailMessage): List<ParsedTransaction>
}
