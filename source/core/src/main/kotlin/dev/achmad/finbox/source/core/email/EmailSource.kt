package dev.achmad.finbox.source.core.email

import dev.achmad.finbox.source.core.annotation.SourceProvider
import dev.achmad.finbox.source.core.ParsedTransaction
import dev.achmad.finbox.source.core.Source
import dev.achmad.finbox.source.core.SourceEntry

/** A provider that publishes transactions by email. */
@SourceProvider
interface EmailSource : Source {

    /**
     * Which mail is worth fetching.
     *
     * A val on the interface rather than a method on the source, so that
     * configuration a source needs belongs to that source. A later PdfSource
     * wanting a password hint declares one on itself, and nothing implementing
     * this recompiles.
     */
    val query: EmailQuery

    /** Read [email] into transactions, or return nothing to disown it. */
    suspend fun parse(email: Email): List<ParsedTransaction>
}

/**
 * This entry's source as an [EmailSource], or null if it reads something else.
 *
 * The type check is the whole of the question — a source that implements the
 * interface can be asked, and one that does not cannot. Callers read better for
 * having a name for it.
 */
val SourceEntry.email: EmailSource? get() = source as? EmailSource
