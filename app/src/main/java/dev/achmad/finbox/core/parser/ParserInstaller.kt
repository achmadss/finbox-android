package dev.achmad.finbox.core.parser

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
 * Downloads a parser APK, verifies its SHA-256 against the repo index
 * (single trusted repo, so hash verification = trust) and copies it into
 * `filesDir/parsers/`. Old versions of the same provider are removed.
 */
class ParserInstaller(
    private val client: OkHttpClient,
    private val loader: ParserLoader,
) {

    /**
     * The install as it happens, so a row can show where it is and offer to stop it.
     *
     * Nothing is handed to a system installer, so dropping the collector really
     * does end it: the download stops and no file was written yet.
     */
    fun downloadAndInstall(parser: AvailableParser): Flow<InstallStep> = flow {
        emit(InstallStep.Pending)
        emit(InstallStep.Downloading)
        // No cache control: an APK would evict everything else from the 5 MiB cache.
        val bytes = client.get(parser.apkUrl, cacheControl = null).use { it.body.bytes() }

        emit(InstallStep.Installing)
        withContext(Dispatchers.IO) { store(parser, bytes) }
        emit(InstallStep.Installed)
    }.catch { e ->
        if (e is CancellationException) throw e
        Log.e("Parsers", "Install failed for ${parser.pkg}", e)
        emit(InstallStep.Error)
    }

    fun remove(pkg: String) {
        loader.parsersDir().listFiles { f ->
            f.extension == "apk" && f.name.startsWith("$pkg-")
        }?.forEach { it.delete() }
    }

    private fun store(parser: AvailableParser, bytes: ByteArray) {
        val actualHash = sha256(bytes)
        if (!actualHash.equals(parser.sha256, ignoreCase = true)) {
            throw IOException("SHA-256 mismatch: expected ${parser.sha256}, got $actualHash")
        }

        // Remove older versions of the same package (same provider pkg prefix).
        val dir = loader.parsersDir()
        dir.listFiles { f -> f.name.startsWith("${parser.pkg}-") && f.extension == "apk" }
            ?.forEach { it.delete() }

        val target = File(dir, "${parser.pkg}-${parser.versionName}.apk")
        target.writeBytes(bytes)
        // Android 14 refuses to load a dex file the app can still write to.
        target.setReadOnly()
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
