package dev.achmad.finbox.core.extension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import androidx.core.content.ContextCompat

/**
 * Tells the app when an extension package arrives, changes or goes.
 *
 * Registered at runtime rather than in the manifest: the app only cares while it
 * is running, and a manifest receiver would wake it for every package event on
 * the device.
 *
 * It carries the install-session results too, because the app cannot claim an
 * install succeeded — the system asks the user, and the answer comes back here.
 */
class ExtensionInstallReceiver(
    private val onAdded: (String) -> Unit,
    private val onRemoved: (String) -> Unit,
    private val onStatus: (String, InstallStep) -> Unit,
    private val onUserAction: (Intent) -> Unit,
) : BroadcastReceiver() {

    fun register(context: Context) {
        ContextCompat.registerReceiver(
            context,
            this,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            context,
            this,
            IntentFilter(ACTION_INSTALL_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_INSTALL_STATUS -> handleStatus(intent)
            Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED ->
                intent.packageName()?.let(onAdded)
            Intent.ACTION_PACKAGE_REMOVED ->
                // An update arrives as remove-then-add. Acting on the remove
                // would drop the extension from the list and put it back a
                // moment later, which reads as a flicker and, worse, as an
                // uninstall to anything watching.
                if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    intent.packageName()?.let(onRemoved)
                }
        }
    }

    private fun handleStatus(intent: Intent) {
        val pkg = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            // The system wants to show its dialog and needs an activity to show
            // it from. Until this is launched, nothing happens at all.
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.let(onUserAction)
            }
            // ACTION_PACKAGE_ADDED does the actual reloading; this only moves
            // the row off "installing".
            PackageInstaller.STATUS_SUCCESS -> onStatus(pkg, InstallStep.Installed)
            else -> onStatus(pkg, InstallStep.Error)
        }
    }

    private fun Intent.packageName(): String? = data?.schemeSpecificPart

    companion object {
        const val ACTION_INSTALL_STATUS = "dev.achmad.finbox.INSTALL_STATUS"
    }
}
