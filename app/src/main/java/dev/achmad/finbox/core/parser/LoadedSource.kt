package dev.achmad.finbox.core.parser

import dev.achmad.finbox.parser.TransactionKind
import dev.achmad.finbox.parser.TransactionSource
import java.security.MessageDigest

/**
 * A source loaded from a parser APK, plus the identity the app files it
 * under. Parsers only ship the [TransactionSource]; name and version come
 * from the APK manifest, so nothing has to be written down twice.
 */
class LoadedSource(
    val id: Long,
    /**
     * The APK's package. Unlike [id] this survives an update, which is what the
     * user's per-kind switches are filed under.
     */
    val pkg: String,
    val provider: String,
    val name: String,
    source: TransactionSource,
) : TransactionSource by source {

    /** The kind with this key, or null — a parser may drop one in an update. */
    fun kindOf(key: String?): TransactionKind? = kinds.firstOrNull { it.key == key }
}

/**
 * The id convention: first 8 bytes of `MD5("${name.lowercase()}/$versionCode")`
 * as a positive Long.
 *
 * Deterministic, so a reinstall of the same parser files its transactions
 * under the same source and nothing is orphaned.
 *
 * Not stable across releases, deliberately: `versionCode` is in the hash, so an
 * updated parser is a source no email has tried, and mail nothing could read
 * before gets another chance. Already-parsed emails are never re-read, so this
 * cannot duplicate a transaction — it does leave rows filed under the version
 * that parsed them.
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
