package dev.achmad.finbox.core.extension

import dev.achmad.finbox.core.FinboxConfig
import dev.achmad.finbox.util.network.get
import dev.achmad.finbox.util.network.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.CacheControl
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
    @SerialName("icon") val icon: String? = null,
)

/** Fetches the extension repo index. */
class ExtensionIndex(
    private val client: OkHttpClient,
) {

    suspend fun fetch(): List<AvailableExtension> {
        // Always over the wire: a cached copy hides a release for as long as it
        // stays fresh.
        val parsed = client.get(
            url = FinboxConfig.EXTENSION_INDEX_URL,
            cacheControl = CacheControl.FORCE_NETWORK,
        ).parseAs<ExtensionIndexResponse>()
        return parsed.extensions.mapNotNull { entry ->
            val libVersion = entry.libVersion.toDoubleOrNull() ?: return@mapNotNull null
            if (!FinboxConfig.supportsLibVersion(libVersion)) return@mapNotNull null
            AvailableExtension(
                name = entry.name,
                provider = entry.provider,
                pkg = entry.pkg,
                versionCode = entry.versionCode,
                versionName = entry.versionName,
                libVersion = libVersion,
                apkUrl = entry.apk,
                sha256 = entry.sha256,
                iconUrl = entry.icon,
            )
        }
    }
}
