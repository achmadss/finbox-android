package dev.achmad.data.model

data class InstalledExtension(
    val pkg: String,
    val name: String,
    val versionCode: Int,
    val versionName: String,
    val libVersion: String,
    /** ISO 3166-1 alpha-2, or empty for an extension that declares none. */
    val country: String,
    val extensionIds: List<String>,
    val enabled: Boolean,
)
