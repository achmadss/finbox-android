package dev.achmad.finbox.core.extension

import dev.achmad.finbox.extension.EmailSource
import dev.achmad.finbox.extension.Source

/** What an extension turned out to be, plus the identity the app files it under. */
class LoadedExtension(
    /**
     * The package name, which is the identity — unique, stable across releases,
     * and already what every part of this system means by "which extension".
     *
     * It used to be the first 8 bytes of MD5(pkg) as a Long, because the column
     * was an integer. The column is text now, so the hash bought nothing and
     * cost a database dump, a backup file and the report tool their
     * readability.
     */
    val id: String,
    val name: String,
    val source: Source,
) {
    /** The id, said the other way. Kept so call sites read as they mean. */
    val pkg: String get() = id

    /**
     * This extension's email source, or null if it reads something else.
     *
     * A type check rather than anything the extension declares, so it cannot
     * disagree with what the class actually implements. A second source kind is
     * one more accessor here and nothing else.
     */
    val email: EmailSource? get() = source as? EmailSource
}
