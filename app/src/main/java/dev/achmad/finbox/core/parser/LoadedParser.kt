package dev.achmad.finbox.core.parser

import dev.achmad.finbox.parser.EmailParser
import java.security.MessageDigest

/**
 * A loaded parser, plus the identity the app files it under. An APK ships only
 * the [EmailParser]; name and version come from its manifest.
 */
class LoadedParser(
    val id: Long,
    /** Survives an update, unlike [id] — so the method switches are filed under it. */
    val pkg: String,
    val provider: String,
    val name: String,
    parser: EmailParser,
) : EmailParser by parser

/**
 * First 8 bytes of `MD5("<name>/<versionCode>")` as a positive Long.
 *
 * Deterministic, so a reinstall files its transactions under the same id and
 * nothing is orphaned. Deliberately not stable across releases: `versionCode`
 * is in the hash, so an updated parser is one no email has tried and mail
 * nothing could read before gets another chance. Parsed emails are never
 * re-read, so this cannot duplicate a transaction — it does leave rows filed
 * under the version that parsed them.
 */
fun parserIdOf(name: String, versionCode: Int): Long {
    val digest = MessageDigest.getInstance("MD5")
        .digest("${name.lowercase()}/$versionCode".toByteArray())
    var value = 0L
    for (i in 0 until 8) {
        value = (value shl 8) or (digest[i].toLong() and 0xff)
    }
    return value and Long.MAX_VALUE
}
