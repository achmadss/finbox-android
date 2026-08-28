package dev.achmad.finbox.extension

import dev.achmad.finbox.extension.bni.Bni
import dev.achmad.finbox.extension.bri.Bri
import dev.achmad.finbox.extension.jago.Jago
import dev.achmad.finbox.extension.mandiri.Mandiri

/**
 * One bank reader, as the app sees it.
 *
 * [source] is the whole of what it can do, and its type is the whole of what it
 * declares — see [Source]. The app never names a bank class; it reads this
 * list, which is why bank knowledge stays in this module.
 */
data class Extension(
    /**
     * Stable, short, and chosen once.
     *
     * Stored on every transaction and in `account_extension`, so it is a real
     * contract with the database and renaming one costs a reimport.
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
 * A hand-written list, because there is nothing to discover: extensions compile
 * into the app, so the compiler already knows all four and a registry that went
 * looking for them would only be able to find the same four later. Adding a
 * bank is a class and a line here.
 */
object Extensions {

    val all: List<Extension> = listOf(
        Extension(id = "bni", name = "Bank BNI", source = Bni()),
        Extension(id = "bri", name = "Bank BRI", source = Bri()),
        Extension(id = "jago", name = "Bank Jago", source = Jago()),
        Extension(id = "mandiri", name = "Bank Mandiri", source = Mandiri()),
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): Extension? = byId[id]
}
