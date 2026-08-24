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
 * First 8 bytes of `MD5(pkg)` as a positive Long.
 *
 * Deterministic, so a reinstall files its transactions under the same id and
 * nothing is orphaned. Stable across releases too, which is the whole point: a
 * parser that updates is still the same parser, and the mail it read is still
 * its mail.
 *
 * `versionCode` used to be in the hash, so that an updated parser counted as
 * one no email had tried and mail nothing could read before got another chance.
 * That worked, but it made an update an amnesiac — the rows and emails it had
 * already claimed pointed at an id that no longer existed, so nothing could
 * find them and a published fix never reached the data it was written for.
 * Retrying is now asked for outright, by re-reading a parser's own mail when it
 * updates, which is clearer than arranging for it to happen as a side effect of
 * an identity change.
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
