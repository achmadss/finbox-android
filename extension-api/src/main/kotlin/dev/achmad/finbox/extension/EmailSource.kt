package dev.achmad.finbox.extension

/** A provider that publishes transactions by email. */
interface EmailSource : Source {

    /**
     * Which mail is worth fetching.
     *
     * A val on the interface rather than a method on the extension, so that
     * configuration a source needs belongs to that source. A later PdfSource
     * wanting a password hint declares one on itself, and nothing implementing
     * this recompiles.
     */
    val query: EmailQuery

    /** Read [email] into transactions, or return nothing to disown it. */
    suspend fun parse(email: Email): List<ParsedTransaction>
}
