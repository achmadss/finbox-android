package dev.achmad.finbox.extension

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Downloads an extension APK, verifies its SHA-256 against the repo index
 * (single trusted repo, so hash verification = trust) and copies it into
 * `filesDir/exts/`. Old versions of the same provider are removed.
 */
class ExtensionInstaller(
    private val context: Context,
    private val client: OkHttpClient,
    private val loader: ExtensionLoader,
) {

    suspend fun install(extension: AvailableExtension): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(extension.apkUrl).get().build()
            val bytes = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download failed: ${response.code}")
                response.body?.bytes() ?: throw IOException("Empty response")
            }

            val actualHash = sha256(bytes)
            if (!actualHash.equals(extension.sha256, ignoreCase = true)) {
                throw IOException("SHA-256 mismatch: expected ${extension.sha256}, got $actualHash")
            }

            // Remove older versions of the same package (same provider pkg prefix).
            val targetName = "${extension.pkg}-${extension.versionName}.apk"
            val dir = loader.extsDir()
            dir.listFiles { f -> f.name.startsWith("${extension.pkg}-") && f.extension == "apk" }
                ?.forEach { it.delete() }

            val target = File(dir, targetName)
            target.writeBytes(bytes)
            target.absolutePath
        }
    }

    fun remove(pkg: String) {
        loader.extsDir().listFiles { f ->
            f.extension == "apk" && f.name.startsWith("$pkg-")
        }?.forEach { it.delete() }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
