package dev.achmad.finbox.extension

import dev.achmad.finbox.extension.core.annotation.SourceEntrypoint
import dev.achmad.finbox.extension.core.source.Source
import dev.achmad.finbox.extension.core.source.email.EmailSource

/**
 * One bank reader, as the app sees it.
 *
 * [source] is the whole of what it can do, and its type is the whole of what it
 * declares — see [Source]. The app never names a bank class; it reads this
 * list, which is why bank knowledge stays in this module.
 */
data class Extension(
    /**
     * Stable, short, and chosen once. See [SourceEntrypoint.id], which is where
     * it is written.
     */
    val id: String,
    /** What the user reads. */
    val name: String,
    val source: Source,
) {
    /** Null for an extension that reads something other than email. */
    val email: EmailSource? get() = source as? EmailSource
}

/**
 * Every extension this build ships.
 *
 * The list is generated: `:extension-processor` collects each
 * [SourceEntrypoint] at compile time, so adding a bank is a class and an
 * annotation, and there is no second place to forget. What is left here is the
 * lookup, which is a fact about how the app reads the list rather than about
 * what is in it.
 */
object Extensions {

    val all: List<Extension> = GeneratedExtensions.all

    private val byId = all.associateBy { it.id }

    fun byId(id: String): Extension? = byId[id]
}
