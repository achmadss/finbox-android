package dev.achmad.finbox.core.extension

import dev.achmad.finbox.extension.EmailSource
import java.security.MessageDigest

/** An [EmailSource], plus the identity the app files it under. */
class LoadedExtension(
    val id: Long,
    /** Survives an update, unlike [id] — so the method switches are filed under it. */
    val pkg: String,
    val name: String,
    extension: EmailSource,
) : EmailSource by extension

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
