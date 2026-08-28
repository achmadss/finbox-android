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

    /**
     * Read, listed, and never instantiated.
     *
     * Its own result rather than an error: nothing is wrong with the package,
     * the user has simply not said yes to this signer yet. The UI can offer
     * that, which it cannot do for an error.
     */
    data class Untrusted(val info: InstalledExtensionInfo) : LoadResult()

    data class Error(val pkg: String, val reason: String) : LoadResult()
}

data class InstalledExtensionInfo(
    val pkg: String,
    val name: String,
    val versionCode: Int,
    val versionName: String,
    val libVersion: Double,
    /** ISO 3166-1 alpha-2, or empty for an extension that declares none. */
    val country: String,
    val className: String,
    /** SHA-256 of the signing certificate. Empty when it could not be read. */
    val signature: String,
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
    /** ISO 3166-1 alpha-2, or empty for an entry that names none. */
    val country: String,
    /** Null for an extension published before icons existed. */
    val iconUrl: String?,
)
