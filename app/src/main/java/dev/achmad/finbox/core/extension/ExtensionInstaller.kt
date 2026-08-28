package dev.achmad.finbox.core.extension

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.util.Log
import dev.achmad.finbox.util.network.get
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
 * Downloads an extension APK and hands it to the system installer.
 *
 * The download is verified against the index's sha256, which says the bytes
 * arrived intact. What is allowed to *run* is decided by [ExtensionTrust] from
 * the signing certificate, once the package is installed.
 */
class ExtensionInstaller(
    private val context: Context,
    private val client: OkHttpClient,
) {

    /**
     * The install as it happens, so a row can show where it is and offer to stop it.
     *
     * Cancellable up to the point the bytes reach the system installer, and not
     * after: from there the system owns the transaction and the app has no say.
     * [InstallStep.Installing] is where that line is, and it is also when the
     * user sees the system's own dialog.
     */
    fun downloadAndInstall(extension: AvailableExtension): Flow<InstallStep> = flow {
        emit(InstallStep.Pending)
        emit(InstallStep.Downloading)
        // No cache control: an APK would evict everything else from the 5 MiB cache.
        val bytes = client.get(extension.apkUrl, cacheControl = null).use { it.body.bytes() }
        verify(extension, bytes)

        emit(InstallStep.Installing)
        withContext(Dispatchers.IO) { commit(extension, bytes) }
        // Not Installed: the system dialog is up and the user has not answered.
        // ExtensionInstallReceiver reports what actually happened, so claiming
        // success here would show an install that the user is about to refuse.
    }.catch { e ->
        if (e is CancellationException) throw e
        Log.e("Extensions", "Install failed for ${extension.pkg}", e)
        emit(InstallStep.Error)
    }

    /**
     * Asks the system to uninstall [pkg].
     *
     * The system asks the user and does the work; the app does not get to
     * decide. Its transactions stay either way — uninstalling an extension is
     * not deleting a ledger.
     */
    fun remove(pkg: String) {
        val intent = Intent(Intent.ACTION_DELETE, "package:$pkg".toUriCompat())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun verify(extension: AvailableExtension, bytes: ByteArray) {
        val actual = sha256(bytes)
        if (!actual.equals(extension.sha256, ignoreCase = true)) {
            throw IOException("SHA-256 mismatch: expected ${extension.sha256}, got $actual")
        }
    }

    private fun commit(extension: AvailableExtension, bytes: ByteArray) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply {
            setAppPackageName(extension.pkg)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite(extension.pkg, 0, bytes.size.toLong()).use { out ->
                out.write(bytes)
                session.fsync(out)
            }
            session.commit(statusReceiver(sessionId).intentSender)
        }
    }

    /**
     * Where the system reports the outcome.
     *
     * MUTABLE because the system fills the result in; IMMUTABLE would arrive
     * empty. The session id keeps two concurrent installs from sharing one
     * pending intent, which would deliver both results to whichever registered last.
     */
    private fun statusReceiver(sessionId: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        sessionId,
        Intent(ExtensionInstallReceiver.ACTION_INSTALL_STATUS).setPackage(context.packageName),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}

private fun String.toUriCompat(): Uri = Uri.parse(this)
