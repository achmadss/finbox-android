package dev.achmad.finbox.parser

/**
 * One financial provider — BRI, Jago, Gojek — as a parser implements it.
 * A source confirms which emails are its own transaction mails and reads them.
 *
 * Fetching is the app's job, but a source says which mail is worth fetching:
 * downloading one message costs twenty times listing five hundred ids, so a
 * source that names its sender saves the app a mailbox it has no use for.
 *
 * Identity (name, version) is not part of this: the app takes it from the APK
 * manifest, which the `finbox { }` block in Gradle fills in. Annotate the
 * implementation with [Source] to make it the APK's entry point.
 */
interface TransactionSource {

    /**
     * Every kind of transaction this source can produce, in the order the user
     * should see them.
     *
     * The app lists these on the parser's page with a switch each, and skips
     * a transaction whose kind is switched off — so a kind missing from here is
     * a kind the user can never turn off, and one whose [TransactionKind.key]
     * never appears in [parseEmail] is a dead switch. Declare a catch-all for
     * the receipts this source can't classify; a bank adds types without
     * warning, and the alternative is dropping them silently.
     */
    val kinds: List<TransactionKind>

    /**
     * Which mail is worth fetching, e.g. `EmailQuery.from("BankBRI@bri.co.id")`.
     *
     * Deliberately wide: this only decides what gets downloaded, and
     * [isEmailForProvider] still rejects the OTPs and promotions a bank sends
     * from the same address. Matching on subjects belongs there too — a bank
     * adds transaction types whenever it likes.
     */
    val emailQuery: EmailQuery

    /**
     * Whether this email is really one of this provider's transaction mails.
     * A bank sends statements, OTPs and promotions from the same address, and
     * those must not reach [parseEmail].
     */
    fun isEmailForProvider(email: EmailMessage): Boolean

    /** Convert the email into one or more standardized transactions. */
    suspend fun parseEmail(email: EmailMessage): List<ParsedTransaction>
}
