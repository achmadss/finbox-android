package dev.achmad.finbox.core.parser

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
    data class Success(val parser: InstalledParserInfo, val source: LoadedSource) : LoadResult()
    data class Error(val file: String, val reason: String) : LoadResult()
}

/** Metadata of an installed parser loaded from its APK. */
data class InstalledParserInfo(
    val pkg: String,
    val provider: String,
    val name: String,
    val versionCode: Int,
    val versionName: String,
    val libVersion: Double,
    val className: String,
)

/** A parser entry from the repo index.json. */
data class AvailableParser(
    val name: String,
    val provider: String,
    val pkg: String,
    val versionCode: Int,
    val versionName: String,
    val libVersion: Double,
    val apkUrl: String,
    val sha256: String,
    /** Null for a parser published before icons existed. */
    val iconUrl: String?,
)
