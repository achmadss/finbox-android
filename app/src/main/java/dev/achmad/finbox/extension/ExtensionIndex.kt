package dev.achmad.finbox.extension

import dev.achmad.finbox.config.FinboxConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

@Serializable
data class ExtensionIndexResponse(
    val extensions: List<ExtensionIndexEntry> = emptyList(),
)

@Serializable
data class ExtensionIndexEntry(
    val name: String,
    val provider: String,
    val pkg: String,
    val version_code: Int,
    val version_name: String,
    val lib_version: String,
    val apk: String,
    val sha256: String,
)

/** Fetches the extension repo index (single hardcoded repo). */
class ExtensionIndex(
    private val client: OkHttpClient,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(): List<AvailableExtension> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(FinboxConfig.EXTENSION_INDEX_URL)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Index fetch failed: ${response.code}")
            val body = response.body?.string().orEmpty()
            val parsed = json.decodeFromString<ExtensionIndexResponse>(body)
            parsed.extensions.filter { it.lib_version in FinboxConfig.SUPPORTED_LIB_VERSIONS }
                .map {
                    AvailableExtension(
                        name = it.name,
                        provider = it.provider,
                        pkg = it.pkg,
                        versionCode = it.version_code,
                        versionName = it.version_name,
                        libVersion = it.lib_version,
                        apkUrl = it.apk,
                        sha256 = it.sha256,
                    )
                }
        }
    }
}
