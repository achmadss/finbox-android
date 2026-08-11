package dev.achmad.finbox.core.extension

import dev.achmad.finbox.core.FinboxConfig
import dev.achmad.finbox.core.network.get
import dev.achmad.finbox.core.network.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient

@Serializable
data class ExtensionIndexResponse(
    @SerialName("extensions") val extensions: List<ExtensionIndexEntry> = emptyList(),
)

@Serializable
data class ExtensionIndexEntry(
    @SerialName("name") val name: String,
    @SerialName("provider") val provider: String,
    @SerialName("pkg") val pkg: String,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    @SerialName("lib_version") val libVersion: String,
    @SerialName("apk") val apk: String,
    @SerialName("sha256") val sha256: String,
)

/** Fetches the extension repo index (single hardcoded repo). */
class ExtensionIndex(
    private val client: OkHttpClient,
) {

    suspend fun fetch(): List<AvailableExtension> {
        val parsed = client.get(FinboxConfig.EXTENSION_INDEX_URL)
            .parseAs<ExtensionIndexResponse>()
        // Entries the app could not load are dropped here rather than offered
        // and then rejected by ExtensionLoader.
        return parsed.extensions.mapNotNull { entry ->
            val libVersion = entry.libVersion.toDoubleOrNull() ?: return@mapNotNull null
            if (libVersion !in FinboxConfig.SUPPORTED_LIB_VERSIONS) return@mapNotNull null
            AvailableExtension(
                name = entry.name,
                provider = entry.provider,
                pkg = entry.pkg,
                versionCode = entry.versionCode,
                versionName = entry.versionName,
                libVersion = libVersion,
                apkUrl = entry.apk,
                sha256 = entry.sha256,
            )
        }
    }
}
