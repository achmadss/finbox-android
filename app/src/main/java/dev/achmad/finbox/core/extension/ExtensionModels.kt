package dev.achmad.finbox.core.extension

sealed class LoadResult {
    data class Success(val extension: InstalledExtensionInfo, val source: TransactionSource) : LoadResult()
    data class Error(val file: String, val reason: String) : LoadResult()
}

/** Metadata of an installed extension loaded from its APK. */
data class InstalledExtensionInfo(
    val pkg: String,
    val provider: String,
    val name: String,
    val versionCode: Int,
    val versionName: String,
    val libVersion: Double,
    val className: String,
)

/** An extension entry from the repo index.json. */
data class AvailableExtension(
    val name: String,
    val provider: String,
    val pkg: String,
    val versionCode: Int,
    val versionName: String,
    val libVersion: Double,
    val apkUrl: String,
    val sha256: String,
)
