package dev.achmad.data.model

data class InstalledExtension(
    val pkg: String,
    val name: String,
    val file: String,
    val versionCode: Int,
    val versionName: String,
    val libVersion: String,
    val sha256: String,
    val extensionIds: List<Long>,
    val enabled: Boolean,
)
