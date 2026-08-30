package dev.achmad.finbox.gradle

import org.gradle.api.provider.Property

/**
 * What a source declares about itself, in its own `build.gradle.kts`:
 *
 * ```kotlin
 * source {
 *     id = "bni"
 *     name = "Bank BNI"
 * }
 * ```
 *
 * Both are required, and the build fails without them.
 */
abstract class SourceExtension {

    /**
     * Short, lowercase, and equal to this module's directory name.
     *
     * Stored on every transaction and in `account_source`, so renaming it costs
     * a reimport. It is written out rather than only inferred from the
     * directory so that it is visible where a contributor is already looking,
     * and checked against the directory so the two cannot drift.
     */
    abstract val id: Property<String>

    /** What the user reads. */
    abstract val name: Property<String>
}
