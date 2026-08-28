package dev.achmad.finbox.source.core

/**
 * One provider the app can read transactions from.
 *
 * Identity and capability are the same object. What a source *is* — its id, its
 * name, its icon — it declares as properties; what it can *do* it declares by
 * implementing an interface, and the app works that out with a type check rather
 * than by reading a list the source wrote about itself. A source that claimed a
 * capability it did not have would need validating somewhere and could still be
 * wrong; one that implements the interface cannot lie.
 *
 * Only [dev.achmad.finbox.source.core.email.EmailSource] exists so far. A second
 * kind is a new interface extending this one, and no existing signature changes.
 */
interface Source {

    /**
     * Stable, short, lowercase, and chosen once.
     *
     * Stored on every transaction and in `account_source`, so it is a real
     * contract with the database: renaming one costs a reimport. It is written
     * out rather than derived from the class or package name, because a
     * refactor must not be able to rename it for you.
     */
    val id: String

    /** What the user reads. */
    val name: String

    /**
     * A drawable in this source's own module.
     *
     * An `Int` rather than anything Android-typed, so the contract stays off
     * AGP. Each source module owns its icon and names it under that module's
     * `resourcePrefix`, which is what keeps four `ic_icon.png` files from
     * merging into whichever one the app happened to link last.
     */
    val icon: Int
}
