package dev.achmad.finbox.extension.core.annotation

/**
 * Marks a class as one extension, to be collected into the registry at compile
 * time.
 *
 * The class must be concrete, constructible with no arguments, and implement at
 * least one [SourceProvider] interface. All three are build errors rather than
 * something the app discovers at runtime — there is no runtime discovery left to
 * discover it.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class SourceEntrypoint(
    /**
     * Stable, short, lowercase, and chosen once.
     *
     * Stored on every transaction and in `account_extension`, so it is a real
     * contract with the database: renaming one costs a reimport. It is written
     * here rather than derived from the class or package name, because a
     * refactor must not be able to silently rename it.
     */
    val id: String,
    /** What the user reads. */
    val name: String,
)
