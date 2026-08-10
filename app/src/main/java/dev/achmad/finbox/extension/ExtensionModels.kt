package dev.achmad.finbox.extension

sealed class LoadResult {
    data class Success(val extension: InstalledExtensionInfo, val sources: List<TransactionSource>) : LoadResult()
    data class Error(val file: String, val reason: String) : LoadResult()
}

/** Metadata of an installed extension loaded from its APK. */
data class InstalledExtensionInfo(
    val pkg: String,
    val provider: String,
    val name: String,
    val versionCode: Int,
    val versionName: String,
    val libVersion: String,
    val className: String,
)

/** An extension entry from the repo index.json. */
data class AvailableExtension(
    val name: String,
    val provider: String,
    val pkg: String,
    val versionCode: Int,
    val versionName: String,
    val libVersion: String,
    val apkUrl: String,
    val sha256: String,
)

/** A normalized email handed to parsers. */
typealias NormalizedEmail = EmailMessage
