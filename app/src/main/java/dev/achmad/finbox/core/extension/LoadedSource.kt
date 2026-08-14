package dev.achmad.finbox.core.extension

import dev.achmad.finbox.extension.TransactionSource
import java.security.MessageDigest

/**
 * A source loaded from an extension APK, plus the identity the app files it
 * under. Extensions only ship the [TransactionSource]; name and version come
 * from the APK manifest, so nothing has to be written down twice.
 */
class LoadedSource(
    val id: Long,
    val provider: String,
    val name: String,
    source: TransactionSource,
) : TransactionSource by source

/**
 * The id convention: first 8 bytes of `MD5("${name.lowercase()}/$versionCode")`
 * as a positive Long.
 *
 * Deterministic, so a reinstall of the same extension files its transactions
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
