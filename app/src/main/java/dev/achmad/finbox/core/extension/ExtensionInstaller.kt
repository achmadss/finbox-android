package dev.achmad.finbox.core.extension

import android.util.Log
import dev.achmad.finbox.util.network.get
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Downloads an extension APK, verifies its SHA-256 against the repo index (single
 * trusted repo, so the hash is trust), and copies it into `filesDir/extensions/`.
 */
class ExtensionInstaller(
    private val client: OkHttpClient,
    private val loader: ExtensionLoader,
) {

    /**
     * The install as it happens, so a row can show where it is and offer to stop it.
     *
     * Dropping the collector cancels it: nothing is handed to a system installer.
     */
    fun downloadAndInstall(extension: AvailableExtension): Flow<InstallStep> = flow {
        emit(InstallStep.Pending)
        emit(InstallStep.Downloading)
        // No cache control: an APK would evict everything else from the 5 MiB cache.
        val bytes = client.get(extension.apkUrl, cacheControl = null).use { it.body.bytes() }

        emit(InstallStep.Installing)
        withContext(Dispatchers.IO) { store(extension, bytes) }
        emit(InstallStep.Installed)
    }.catch { e ->
        if (e is CancellationException) throw e
        Log.e("Extensions", "Install failed for ${extension.pkg}", e)
        emit(InstallStep.Error)
    }

    fun remove(pkg: String) {
        loader.extensionsDir().listFiles { f ->
            f.extension == "apk" && f.name.startsWith("$pkg-")
        }?.forEach { it.delete() }
    }

    private fun store(extension: AvailableExtension, bytes: ByteArray) {
        val actualHash = sha256(bytes)
        if (!actualHash.equals(extension.sha256, ignoreCase = true)) {
            throw IOException("SHA-256 mismatch: expected ${extension.sha256}, got $actualHash")
        }

        val dir = loader.extensionsDir()
        dir.listFiles { f -> f.name.startsWith("${extension.pkg}-") && f.extension == "apk" }
            ?.forEach { it.delete() }

        val target = File(dir, "${extension.pkg}-${extension.versionName}.apk")
        target.writeBytes(bytes)
        // Android 14 refuses to load a dex file the app can still write to.
        target.setReadOnly()
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
