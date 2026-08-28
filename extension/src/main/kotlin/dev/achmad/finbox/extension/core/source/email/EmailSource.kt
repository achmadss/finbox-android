package dev.achmad.finbox.extension.core.source.email

import dev.achmad.finbox.extension.core.annotation.SourceProvider
import dev.achmad.finbox.extension.core.transaction.ParsedTransaction
import dev.achmad.finbox.extension.core.source.Source
import dev.achmad.finbox.extension.core.source.email.model.Email
import dev.achmad.finbox.extension.core.source.email.model.EmailQuery

/** A provider that publishes transactions by email. */
@SourceProvider
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
