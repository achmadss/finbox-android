package dev.achmad.finbox.core.extension

import dev.achmad.finbox.extension.EmailSource
import dev.achmad.finbox.extension.Source
import java.security.MessageDigest

/** What an extension turned out to be, plus the identity the app files it under. */
class LoadedExtension(
    val id: Long,
    /** Survives an update, unlike [id]. */
    val pkg: String,
    val name: String,
    val source: Source,
) {
    /**
     * This extension's email source, or null if it reads something else.
     *
     * A type check rather than anything the extension declares, so it cannot
     * disagree with what the class actually implements. A second source kind is
     * one more accessor here and nothing else.
     */
    val email: EmailSource? get() = source as? EmailSource
}

/**
 * First 8 bytes of `MD5(pkg)` as a positive Long.
 *
 * Deterministic across extension releases, so an updating extension keeps its
 * identity and the mail it read stays its mail.
 */
fun extensionIdOf(pkg: String): Long {
    val digest = MessageDigest.getInstance("MD5")
        .digest(pkg.lowercase().toByteArray())
    var value = 0L
    for (i in 0 until 8) {
        value = (value shl 8) or (digest[i].toLong() and 0xff)
    }
    return value and Long.MAX_VALUE
}
