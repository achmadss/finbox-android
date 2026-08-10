package dev.achmad.domain.model

data class InstalledExtension(
    val pkg: String,
    val provider: String,
    val name: String,
    val file: String,
    val versionCode: Int,
    val versionName: String,
    val libVersion: String,
    val sha256: String,
    val sourceIds: List<Long>,
    val enabled: Boolean,
)
