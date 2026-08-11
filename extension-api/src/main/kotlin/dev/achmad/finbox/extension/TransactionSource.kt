package dev.achmad.finbox.extension

/**
 * A parser extension for one financial provider, as the app sees it:
 * [TransactionParser] behaviour plus a stable identity.
 *
 * Extensions rarely implement this directly — annotate a [TransactionParser]
 * with `@Source` and the build supplies the identity from Gradle. Implement it
 * yourself only from a [SourceFactory], where one APK exposes several parsers.
 */
interface TransactionSource : TransactionParser {

    /** Deterministic and stable across releases; see [sourceIdOf]. */
    val id: Long
    val name: String
    val versionId: Int
}
