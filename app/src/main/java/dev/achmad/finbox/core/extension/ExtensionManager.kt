package dev.achmad.finbox.core.extension

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
    private val transactionUpdateManager: TransactionUpdateManager,
    private val loader: ExtensionLoader,
    private val installer: ExtensionInstaller,
    private val index: ExtensionIndex,
    private val repository: InstalledExtensionRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

    suspend fun refreshIndex() {
        available.value = index.fetch()
    }

    /**
     * The install as a flow of steps, ending in [InstallStep.Installed] once the
     * APK is loaded and the registry has caught up.
     */
    private fun installExtension(extension: AvailableExtension): Flow<InstallStep> =
        installer.downloadAndInstall(extension)
            .onEach { if (it == InstallStep.Installed) reload() }

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
            installExtension(extension)
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
            if (last == InstallStep.Installed) reparseWanted.set(true)
            // The last install of a batch speaks for all of them: asking per
            // extension only gets the first request in, while the rest arrive during
            // the re-read and are turned away.
            if (installJobs.isEmpty() && reparseWanted.getAndSet(false)) reparse()
        }
    }

    fun update(pkg: String) {
        val extension = available.value.firstOrNull { it.pkg == pkg } ?: return
        install(extension)
    }

    /** Ends a download; nothing is written until it finishes, so there is nothing to undo. */
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

    suspend fun remove(pkg: String) {
        installer.remove(pkg)
        repository.delete(pkg)
        // Keyed by package, so they would otherwise outlive it and quietly
        // suppress methods if the same extension were installed again.
        reload()
    }

    suspend fun setEnabled(pkg: String, enabled: Boolean) {
        repository.setEnabled(pkg, enabled)
        reload()
    }

    /** Reloads the APKs and resyncs the database and the in-memory registry. */
    suspend fun reload() = mutex.withLock {
        val results = withContext(Dispatchers.IO) { loader.loadExtensions() }

        val infos = mutableMapOf<InstalledExtensionInfo, LoadedExtension>()
        val errors = mutableMapOf<String, String>()
        for (result in results) {
            when (result) {
                is LoadResult.Success -> infos[result.info] = result.extension
                is LoadResult.Error -> errors[result.file] = result.reason
            }
        }
        installedInfo.value = infos.entries.associate { it.key.pkg to it.key }
        loadErrors.value = errors

        val dbExtensions = repository.extensions().first()
        val dbByPkg = dbExtensions.associateBy { it.pkg }

        withContext(Dispatchers.IO) {
            for ((info, extension) in infos) {
                val existing = dbByPkg[info.pkg]
                val row = InstalledExtension(
                    pkg = info.pkg,
                    name = info.name,
                    file = loader.extensionsDir()
                        .listFiles()
                        ?.firstOrNull { it.extension == "apk" && it.name.startsWith("${info.pkg}-") }
                        ?.absolutePath
                        ?: "",
                    versionCode = info.versionCode,
                    versionName = info.versionName,
                    libVersion = info.libVersion.toString(),
                    sha256 = "",
                    extensionIds = listOf(extension.id),
                    // The APK is the truth about everything except this.
                    enabled = existing?.enabled != false,
                )
                if (row != existing) repository.upsert(row)
            }
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
}

fun List<AvailableExtension>.hasUpdateFor(extension: InstalledExtension): Boolean =
    any { it.pkg == extension.pkg && it.versionCode > extension.versionCode }
