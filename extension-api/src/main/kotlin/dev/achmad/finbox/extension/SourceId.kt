package dev.achmad.finbox.extension

import java.security.MessageDigest

/**
 * The id convention: first 8 bytes of `MD5("${name.lowercase()}/$versionId")`
 * as a positive Long.
 *
 * Deterministic and stable across releases, so transaction rows and per-account
 * parser assignments survive reinstalls. Lives here rather than in each parser
 * so every extension derives ids the same way.
 */
fun sourceIdOf(name: String, versionId: Int): Long {
    val digest = MessageDigest.getInstance("MD5")
        .digest("${name.lowercase()}/$versionId".toByteArray())
    var value = 0L
    for (i in 0 until 8) {
        value = (value shl 8) or (digest[i].toLong() and 0xff)
    }
    return value and Long.MAX_VALUE
}
