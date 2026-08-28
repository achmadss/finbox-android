package dev.achmad.finbox.core.extension

import dev.achmad.finbox.core.preference.ExtensionPreferences
import dev.achmad.finbox.core.update.transaction.TransactionUpdateManager
import dev.achmad.finbox.extension.Extension
import dev.achmad.finbox.extension.Extensions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * The extensions this build ships, and which of them the user has switched off.
 *
 * Almost nothing, compared to what stood here. Extensions used to be separate
 * apps: this class reconciled the device's package list against a database
 * table, drove installs and uninstalls, tracked download progress, and reloaded
 * a classloader when a package arrived. They compile into the app now, so the
 * set is fixed at build time and the only mutable fact is the switch.
 */
class ExtensionManager(
    private val preferences: ExtensionPreferences,
    private val transactionUpdateManager: TransactionUpdateManager,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Everything this build ships, switched off or not. */
    val all: List<Extension> = Extensions.all

    /**
     * The extensions that actually run.
     *
     * A flow because the switch can move while a screen is open; the list it
     * derives from cannot.
     */
    val enabled: StateFlow<List<Extension>> = preferences.disabledExtensions().changes()
        .map { disabled -> all.filterNot { it.id in disabled } }
        .stateIn(scope, SharingStarted.Eagerly, enabledNow())

    fun enabledNow(): List<Extension> {
        val disabled = preferences.disabledExtensions().get()
        return all.filterNot { it.id in disabled }
    }

    fun isEnabled(id: String): Boolean = id !in preferences.disabledExtensions().get()

    /**
     * No re-parse on enabling.
     *
     * A disabled extension is skipped without being marked tried, so mail it
     * missed is still pending for it and the next update reads it. See
     * `TransactionUpdater.extensionsFor`.
     */
    fun setEnabled(id: String, enabled: Boolean) {
        val pref = preferences.disabledExtensions()
        pref.set(if (enabled) pref.get() - id else pref.get() + id)
    }

    /**
     * Re-reads stored mail after an app update, which is the only way an
     * extension can change now.
     *
     * This is why email bodies are stored: an extension that learned to read
     * something re-reads it from the database instead of paying Gmail twenty
     * quota units a message again. It used to run when a package was installed;
     * the trigger moves to the app's own versionCode because that is now the
     * thing that changes.
     *
     * ponytail: reparses everything on any app update, including one that only
     * touched a Compose screen. A per-extension revision int would narrow it —
     * add that when a reparse is slow enough to notice. It reads the local
     * database, not Gmail, so it costs CPU and no quota.
     */
    suspend fun reparseIfAppUpdated(versionCode: Int) {
        val last = preferences.lastParsedAppVersion()
        if (last.get() == versionCode) return
        last.set(versionCode)
        // Enqueuing may toast, and a toast needs the main thread.
        withContext(Dispatchers.Main) {
            // Including mail already parsed: an extension update fixes how it
            // reads, so the rows it wrote before are the ones now wrong.
            // Hand-edited rows are skipped by upsertAll, as always.
            transactionUpdateManager.reparseNow(
                includeParsed = true,
                extensionIds = enabledNow().mapTo(mutableSetOf()) { it.id },
                // The app asked, not the user: a request turned down here is
                // not worth a toast.
                userInitiated = false,
            )
        }
    }
}
