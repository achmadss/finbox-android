package dev.achmad.finbox.parser

/** A financial provider, as a parser implements it. */
interface EmailParser {

    /** Every method this parser can produce, in the order the user should see them. */
    fun methods(): List<TransactionMethod>

    /** Which mail is worth fetching. */
    fun emailQuery(): EmailQuery

    /** Read [email] into transactions, or return nothing to disown it. */
    suspend fun parse(email: Email): List<ParsedTransaction>
}
