package dev.achmad.finbox.parser

/**
 * One financial provider — BRI, Jago, Gojek — as a parser implements it.
 *
 * Identity (name, version) comes from the APK manifest, which the `finbox { }`
 * block in Gradle fills in, so none of it is declared here. Annotate the
 * implementation with [Parser] to make it the APK's entry point.
 */
interface EmailParser {

    /**
     * Every type this parser can produce, in the order the user should see them.
     *
     * The app puts a switch against each and skips a transaction whose type is
     * off, so a type missing from here can never be turned off, and one that
     * never comes back from [parse] is a dead switch. Declare a catch-all: a
     * provider adds types without warning, and the alternative is dropping them
     * silently.
     */
    fun types(): List<TransactionType>

    /**
     * Which mail is worth fetching, e.g. `EmailQuery.from("BankBRI@bri.co.id")`.
     *
     * Fetching is the app's job, but downloading one message costs twenty times
     * listing five hundred ids, so naming a sender saves the app a mailbox it
     * has no use for. Deliberately wide — [parse] does the real rejecting.
     */
    fun emailQuery(): EmailQuery

    /**
     * Read [email] into transactions, or return nothing to disown it.
     *
     * Empty covers both cases the app cares about: mail from another provider,
     * and this provider's own OTPs and promotions, which arrive from the same
     * address as its receipts. The app then offers the email to the next parser,
     * so a wrong guess costs one wasted call.
     */
    suspend fun parse(email: Email): List<ParsedTransaction>
}
