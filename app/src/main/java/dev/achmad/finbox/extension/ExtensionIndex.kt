package dev.achmad.finbox.extension

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
        return parsed.extensions
            .filter { it.libVersion in FinboxConfig.SUPPORTED_LIB_VERSIONS }
            .map {
                AvailableExtension(
                    name = it.name,
                    provider = it.provider,
                    pkg = it.pkg,
                    versionCode = it.versionCode,
                    versionName = it.versionName,
                    libVersion = it.libVersion,
                    apkUrl = it.apk,
                    sha256 = it.sha256,
                )
            }
    }
}
