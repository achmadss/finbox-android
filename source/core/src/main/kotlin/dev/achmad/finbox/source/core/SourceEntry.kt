package dev.achmad.finbox.source.core

/**
 * One source, as the app sees it: who it is, and what it can do.
 *
 * Assembled by the processor from the module's `source {}` block and its icon,
 * so nothing in it is written twice. It is a separate type from [Source]
 * because a source class carries no identity of its own — the build file does —
 * and a type that held both would have to be either half-empty or generated.
 */
data class SourceEntry(
    /**
     * Stable, short, lowercase, and chosen once.
     *
     * Stored on every transaction and in `account_source`, so it is a real
     * contract with the database and renaming one costs a reimport.
     */
    val id: String,
    /** What the user reads. */
    val name: String,
    /**
     * A drawable in this source's own module, built from the `ic_launcher.png`
     * it ships. An `Int` rather than anything Android-typed, so the contract
     * stays off AGP.
     */
    val icon: Int,
    val source: Source,
)
