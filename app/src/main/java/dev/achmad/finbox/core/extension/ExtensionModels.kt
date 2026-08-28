package dev.achmad.finbox.core.extension

/** Where an install or update has got to, for the row it is running on. */
enum class InstallStep {
    Idle,
    Pending,
    Downloading,
    Installing,
    Installed,
    Error,
    ;

    fun isCompleted(): Boolean = this == Installed || this == Error || this == Idle
}

sealed class LoadResult {
    data class Success(val info: InstalledExtensionInfo, val extension: LoadedExtension) : LoadResult()
    data class Error(val file: String, val reason: String) : LoadResult()
}

data class InstalledExtensionInfo(
    val pkg: String,
    val name: String,
    val versionCode: Int,
    val versionName: String,
    val libVersion: Double,
    val className: String,
)

/** An extension entry from the repo index.json. */
data class AvailableExtension(
    val name: String,
    val pkg: String,
    val versionCode: Int,
    val versionName: String,
    val libVersion: Double,
    val apkUrl: String,
    val sha256: String,
    /** Null for an extension published before icons existed. */
    val iconUrl: String?,
)
