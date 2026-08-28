package dev.achmad.finbox.core.extension

import android.content.Context
import android.content.Intent
import dev.achmad.data.model.InstalledExtension
import dev.achmad.data.repository.InstalledExtensionRepository
import dev.achmad.finbox.core.update.transaction.TransactionUpdateManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Orchestrates installed extensions: loading, database sync, installs, and updates. */
class ExtensionManager(
    private val context: Context,
    private val transactionUpdateManager: TransactionUpdateManager,
    private val loader: ExtensionLoader,
    private val installer: ExtensionInstaller,
    private val index: ExtensionIndex,
    private val repository: InstalledExtensionRepository,
    private val trust: ExtensionTrust,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Extensions the user has not trusted yet: read, listed, never run.
     *
     * Separate from [loadErrors] because it is not a failure — the package is
     * fine and the user simply has not said yes to its signer.
     */
    val untrusted: MutableStateFlow<List<InstalledExtensionInfo>> = MutableStateFlow(emptyList())

    private val mutex = Mutex()

    val installedInfo: MutableStateFlow<Map<String, InstalledExtensionInfo>> = MutableStateFlow(emptyMap())

    /** Load failures by file name, shown in the UI. */
    val loadErrors: MutableStateFlow<Map<String, String>> = MutableStateFlow(emptyMap())

    /** Repo index entries fetched from finbox-extension. */
    val available: MutableStateFlow<List<AvailableExtension>> = MutableStateFlow(emptyList())

    val installed: StateFlow<List<InstalledExtension>> = repository.extensions()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Drives the badge, and the notification's dismissal once it reaches zero. */
    val updatesCount: StateFlow<Int> = combine(installed, available) { installed, available ->
        installed.count { inst -> available.hasUpdateFor(inst) }
    }.stateIn(scope, SharingStarted.Eagerly, 0)

    /** pkg -> how far its install has got, for whichever screen is showing it. */
    private val _installSteps = MutableStateFlow<Map<String, InstallStep>>(emptyMap())
    val installSteps: StateFlow<Map<String, InstallStep>> = _installSteps.asStateFlow()

    private val installJobs = ConcurrentHashMap<String, Job>()

    /** Set by an install that landed, cleared by whichever one asks for the re-read. */
    private val reparseWanted = AtomicBoolean(false)

    /** The enabled extensions, by id. */
    private val knownExtensions: LinkedHashMap<String, LoadedExtension> = LinkedHashMap()

    /** Observable, so a screen built before the registry was loaded still sees the extensions. */
    private val _extensionsFlow = MutableStateFlow<List<LoadedExtension>>(emptyList())
    val extensionsFlow: StateFlow<List<LoadedExtension>> = _extensionsFlow.asStateFlow()

    val extensions: List<LoadedExtension>
        get() = _extensionsFlow.value

    /**
     * Watches the device's package list, which is the source of truth now.
     *
     * Registered here rather than in the manifest: the app only cares while it
     * is running.
     */
    private val installReceiver = ExtensionInstallReceiver(
        onAdded = { pkg -> scope.launch { if (loader.packageInfoOf(pkg) != null) onExtensionInstalled(pkg) } },
        onRemoved = { pkg -> scope.launch { reload() } },
        onStatus = { pkg, step ->
            // Only a failure has to stick: a success is followed by
            // ACTION_PACKAGE_ADDED, which reloads and redraws the row from the
            // installed list.
            if (step == InstallStep.Error) _installSteps.update { it + (pkg to step) }
            else _installSteps.update { it - pkg }
        },
        onUserAction = { intent ->
            // The system's confirm dialog. It needs an activity to start from,
            // and this is reached from a broadcast, so it starts its own task.
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
    )

    init {
        installReceiver.register(context)
    }

    suspend fun refreshIndex() {
        available.value = index.fetch()
    }

    /**
     * Downloads and installs [extension], reporting progress through [installSteps].
     *
     * Run here rather than on the screen that asked: leaving that screen must not
     * cancel a download halfway. Only [cancelInstall] stops one.
     */
    fun install(extension: AvailableExtension) {
        val pkg = extension.pkg
        installJobs.remove(pkg)?.cancel()
        // Before the job runs, so a retry stops reading as the failure it is
        // replacing — [installSteps] keeps an error until something supersedes it.
        _installSteps.update { it + (pkg to InstallStep.Pending) }
        installJobs[pkg] = scope.launch {
            var last = InstallStep.Idle
            installer.downloadAndInstall(extension)
                .onEach { step ->
                    last = step
                    _installSteps.update { it + (pkg to step) }
                }
                .onCompletion {
                    installJobs.remove(pkg)
                    // A finished install redraws from the installed list; a
                    // failure has to stay on the row or it looks untouched.
                    if (last != InstallStep.Error) _installSteps.update { it - pkg }
                }
                .collect()
            // Nothing is reloaded here: the flow ends when the bytes reach the
            // system installer, and the user has not answered its dialog yet.
            // ExtensionInstallReceiver is what knows an install happened.
        }
    }

    fun update(pkg: String) {
        val extension = available.value.firstOrNull { it.pkg == pkg } ?: return
        install(extension)
    }

    /**
     * Ends a download.
     *
     * Only possible while it is still downloading — once the bytes reach the
     * system installer the transaction belongs to the system, and the UI stops
     * offering this.
     */
    fun cancelInstall(pkg: String) {
        installJobs.remove(pkg)?.cancel()
        _installSteps.update { it - pkg }
    }

    /**
     * Re-reads mail for the loaded extensions, in a job so it outlives any screen,
     * on the main thread because enqueuing may show a toast.
     */
    private suspend fun reparse() = withContext(Dispatchers.Main) {
        // Including mail already parsed: an extension update fixes how it reads, so
        // the rows it wrote before are the ones now wrong; leaving them alone
        // means the fix never reaches the mail it already read.
        // Safe because an extension keeps its id across versions: existing rows
        // update in place and hand-edited ones are skipped.
        transactionUpdateManager.reparseNow(
            includeParsed = true,
            extensionIds = extensionsFlow.value.mapTo(mutableSetOf()) { it.id },
            // The app asked, not the user: a request turned down here is not
            // worth a toast.
            userInitiated = false,
        )
    }

    fun pendingUpdates(): List<InstalledExtension> =
        installed.value.filter { available.value.hasUpdateFor(it) }

    /**
     * Asks the system to uninstall, and does nothing else.
     *
     * The database row and the registry are cleared when the removal actually
     * happens, which ACTION_PACKAGE_REMOVED reports. Doing it here would drop
     * the extension from the list even when the user says no to the dialog.
     */
    fun remove(pkg: String) = installer.remove(pkg)

    /** The user allowing an extension's signer; it loads on the next pass. */
    suspend fun trustExtension(pkg: String) {
        val info = untrusted.value.firstOrNull { it.pkg == pkg } ?: return
        trust.trust(info.pkg, info.signature)
        reload()
    }

    private suspend fun onExtensionInstalled(pkg: String) {
        reload()
        // An extension that arrived can read mail already stored, so ask for the
        // re-read here rather than at the end of a download that may never have
        // been one — a sideloaded APK never went through install().
        if (extensionsFlow.value.any { it.id == pkg }) {
            reparseWanted.set(true)
            if (installJobs.isEmpty() && reparseWanted.getAndSet(false)) reparse()
        }
    }

    suspend fun setEnabled(pkg: String, enabled: Boolean) {
        repository.setEnabled(pkg, enabled)
        reload()
    }

    /**
     * Re-reads the installed packages and resyncs the database and the registry.
     *
     * The device's package list is the source of truth, so this also reconciles:
     * a package uninstalled while the app was dead leaves a row behind, and a
     * row with no package is a lie.
     */
    suspend fun reload() = mutex.withLock {
        val results = withContext(Dispatchers.IO) { loader.loadExtensions() }

        val infos = mutableMapOf<InstalledExtensionInfo, LoadedExtension>()
        val errors = mutableMapOf<String, String>()
        val blocked = mutableListOf<InstalledExtensionInfo>()
        for (result in results) {
            when (result) {
                is LoadResult.Success -> infos[result.info] = result.extension
                is LoadResult.Untrusted -> blocked += result.info
                is LoadResult.Error -> errors[result.pkg] = result.reason
            }
        }
        installedInfo.value = infos.entries.associate { it.key.pkg to it.key }
        loadErrors.value = errors
        untrusted.value = blocked

        val dbExtensions = repository.extensions().first()
        val dbByPkg = dbExtensions.associateBy { it.pkg }

        withContext(Dispatchers.IO) {
            for ((info, extension) in infos) {
                val existing = dbByPkg[info.pkg]
                val row = InstalledExtension(
                    pkg = info.pkg,
                    name = info.name,
                    versionCode = info.versionCode,
                    versionName = info.versionName,
                    libVersion = info.libVersion.toString(),
                    country = info.country,
                    extensionIds = listOf(extension.id),
                    // The package is the truth about everything except this.
                    enabled = existing?.enabled != false,
                )
                if (row != existing) repository.upsert(row)
            }
            // Anything the package manager no longer reports, including one
            // removed while the app was not running. An untrusted extension is
            // dropped too: it is on the device but it is not running, and a row
            // saying otherwise would let the ledger claim rows it never parsed.
            val loadedPkgs = infos.keys.map { it.pkg }.toSet()
            dbExtensions.filter { it.pkg !in loadedPkgs }.forEach { repository.delete(it.pkg) }
        }

        knownExtensions.clear()
        val fresh = repository.extensions().first().filter { it.enabled }
        val extensionsByPkg = infos.entries.associate { it.key.pkg to it.value }
        for (ext in fresh) {
            extensionsByPkg[ext.pkg]?.let { knownExtensions[it.id] = it }
        }
        _extensionsFlow.value = knownExtensions.values.toList()
    }

    fun getById(extensionId: String): LoadedExtension? = knownExtensions[extensionId]

    /** The size of the installed APK, or null if the package is gone. */
    fun apkSizeOf(pkg: String): Long? =
        loader.packageInfoOf(pkg)?.applicationInfo?.sourceDir
            ?.let { java.io.File(it).length() }
            ?.takeIf { it > 0 }
}

fun List<AvailableExtension>.hasUpdateFor(extension: InstalledExtension): Boolean =
    any { it.pkg == extension.pkg && it.versionCode > extension.versionCode }
