package dev.achmad.finbox.core.extension

import dev.achmad.finbox.extension.TransactionParser
import java.security.MessageDigest

/**
 * A loaded extension's parser plus the identity the app files it under.
 *
 * Extensions only ship the [TransactionParser]; name and version come from the
 * APK manifest, so nothing has to be written down twice.
 */
class TransactionSource(
    val id: Long,
    val name: String,
    parser: TransactionParser,
) : TransactionParser by parser

/**
 * The id convention: first 8 bytes of `MD5("${name.lowercase()}/$versionCode")`
 * as a positive Long.
 *
 * Deterministic and stable across releases, so transaction rows and per-account
 * parser assignments survive reinstalls.
 */
fun sourceIdOf(name: String, versionCode: Int): Long {
    val digest = MessageDigest.getInstance("MD5")
        .digest("${name.lowercase()}/$versionCode".toByteArray())
    var value = 0L
    for (i in 0 until 8) {
        value = (value shl 8) or (digest[i].toLong() and 0xff)
    }
    return value and Long.MAX_VALUE
}
