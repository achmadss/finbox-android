package dev.achmad.finbox.core.parser

import dev.achmad.finbox.parser.EmailParser
import java.security.MessageDigest

/** An [EmailParser], plus the identity the app files it under. */
class LoadedParser(
    val id: Long,
    /** Survives an update, unlike [id] — so the method switches are filed under it. */
    val pkg: String,
    val provider: String,
    val name: String,
    parser: EmailParser,
) : EmailParser by parser

/**
 * First 8 bytes of `MD5(pkg)` as a positive Long.
 *
 * Deterministic across parser releases, so an updating parser keeps its
 * identity and the mail it read stays its mail.
 */
fun parserIdOf(pkg: String): Long {
    val digest = MessageDigest.getInstance("MD5")
        .digest(pkg.lowercase().toByteArray())
    var value = 0L
    for (i in 0 until 8) {
        value = (value shl 8) or (digest[i].toLong() and 0xff)
    }
    return value and Long.MAX_VALUE
}
