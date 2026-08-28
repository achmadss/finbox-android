package dev.achmad.finbox.extension

/** A financial provider, as an extension implements it. */
interface EmailSource {

    /** Every method this extension can produce, in the order the user should see them. */
    fun methods(): List<TransactionMethod>

    /** Which mail is worth fetching. */
    fun emailQuery(): EmailQuery

    /** Read [email] into transactions, or return nothing to disown it. */
    suspend fun parse(email: Email): List<ParsedTransaction>
}
