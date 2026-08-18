package dev.achmad.finbox.core.extension

import android.content.Context
import dev.achmad.data.model.InstalledExtension
import dev.achmad.data.repository.InstalledExtensionRepository
import dev.achmad.finbox.core.statement.StatementUpdateJob
import java.util.concurrent.ConcurrentHashMap
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
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import dev.achmad.finbox.core.preference.ExtensionKindPreference

/**
 * Orchestrates installed extensions:
 * - loads APKs from disk ([reload])
 * - keeps the `installed_extension` DB table in sync
 * - fetches the repo index for the available/update lists
 * - runs installs and updates ([install], [update])
 */
class ExtensionManager(
    private val context: Context,
    private val loader: ExtensionLoader,
    private val installer: ExtensionInstaller,
    private val index: ExtensionIndex,
    private val repository: InstalledExtensionRepository,
    private val kindPreference: ExtensionKindPreference,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val mutex = Mutex()

    /** pkg -> extension metadata for successfully loaded extensions. */
    val installedInfo: MutableStateFlow<Map<String, InstalledExtensionInfo>> = MutableStateFlow(emptyMap())

    /** Load failures by file name, surfaced in the UI. */
    val loadErrors: MutableStateFlow<Map<String, String>> = MutableStateFlow(emptyMap())

    /** Repo index entries fetched from finbox-extension. */
    val available: MutableStateFlow<List<AvailableExtension>> = MutableStateFlow(emptyList())

    val installed: StateFlow<List<InstalledExtension>> = repository.extensions()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** Drives the badge, and the notification's dismissal once it reaches zero. */
    val updatesCount: StateFlow<Int> = combine(installed, available) { installed, available ->
        installed.count { inst -> available.hasUpdateFor(inst) }
    }.stateIn(scope, SharingStarted.Eagerly, 0)

    /** pkg -> how far its install or update has got, for whichever screen is showing it. */
    private val _installSteps = MutableStateFlow<Map<String, InstallStep>>(emptyMap())
    val installSteps: StateFlow<Map<String, InstallStep>> = _installSteps.asStateFlow()

    /** Kept so a cancel button has something to stop. */
    private val installJobs = ConcurrentHashMap<String, Job>()

    /** sourceId -> loaded LoadedSource for enabled extensions. */
    private val knownSources: LinkedHashMap<Long, LoadedSource> = LinkedHashMap()

    /** Observable, so a screen built before the registry was loaded still sees the parsers. */
    private val _sourcesFlow = MutableStateFlow<List<LoadedSource>>(emptyList())
    val sourcesFlow: StateFlow<List<LoadedSource>> = _sourcesFlow.asStateFlow()

    val sources: List<LoadedSource>
        get() = _sourcesFlow.value

    suspend fun refreshIndex() {
        available.value = index.fetch()
    }

    /**
     * The install as a flow of steps, ending in [InstallStep.Installed] once the
     * new APK is loaded and the registry has caught up.
     *
     * Dropping the collector cancels the install, which is exactly why [install]
     * collects it somewhere longer-lived than a screen.
     */
    private fun installExtension(extension: AvailableExtension): Flow<InstallStep> =
        installer.downloadAndInstall(extension)
            .onEach { if (it == InstallStep.Installed) reload() }

    /**
     * Downloads and installs [extension], reporting progress through [installSteps].
     *
     * Run here rather than from the screen that asked: an install outlives the row
     * it started from, and leaving that screen must not cancel a download halfway.
     * Only [cancelInstall] stops one.
     */
    fun install(extension: AvailableExtension) {
        val pkg = extension.pkg
        installJobs.remove(pkg)?.cancel()
        installJobs[pkg] = scope.launch {
            var last = InstallStep.Idle
            installExtension(extension)
                .onEach { step ->
                    last = step
                    _installSteps.update { it + (pkg to step) }
                }
                .onCompletion {
                    installJobs.remove(pkg)
                    // A finished install redraws from the installed list, but a
                    // failure has to stay on the row or it goes back to looking
                    // untouched.
                    if (last != InstallStep.Error) _installSteps.update { it - pkg }
                }
                .collect()
            if (last == InstallStep.Installed) reparse()
        }
    }

    /** Installs the newer build the index has of [pkg]. */
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
     * A parser that wasn't there before reads the mail it hasn't tried yet.
     *
     * Handed to a job: it downloads a body per untried email, which takes longer
     * than any screen is guaranteed to live. On the main thread because enqueuing
     * one may raise a toast.
     */
    private suspend fun reparse() = withContext(Dispatchers.Main) {
        StatementUpdateJob.reparseNow(context)
    }

    /**
     * Pass or fail only, for callers with no row to report steps on and a reason
     * to wait — onboarding, which cannot move on until the parser is there.
     */
    suspend fun installAndWait(extension: AvailableExtension) {
        val last = installExtension(extension).last()
        if (last != InstallStep.Installed) throw IOException("Install failed for ${extension.pkg}")
    }

    /** Installed extensions the index has a newer build of. */
    fun pendingUpdates(): List<InstalledExtension> =
        installed.value.filter { available.value.hasUpdateFor(it) }

    suspend fun remove(pkg: String) {
        installer.remove(pkg)
        repository.delete(pkg)
        // The switches are keyed by package and would otherwise outlive it,
        // quietly suppressing kinds if the same extension is installed again.
        kindPreference.clear(pkg)
        reload()
    }

    suspend fun setEnabled(pkg: String, enabled: Boolean) {
        repository.setEnabled(pkg, enabled)
        reload()
    }

    /** Reloads APKs from disk and resyncs the DB + in-memory source registry. */
    suspend fun reload() = mutex.withLock {
        val results = withContext(Dispatchers.IO) { loader.loadExtensions() }

        val infos = mutableMapOf<InstalledExtensionInfo, LoadedSource>()
        val errors = mutableMapOf<String, String>()
        for (result in results) {
            when (result) {
                is LoadResult.Success -> infos[result.extension] = result.source
                is LoadResult.Error -> errors[result.file] = result.reason
            }
        }
        installedInfo.value = infos.entries.associate { it.key.pkg to it.key }
        loadErrors.value = errors

        val dbExtensions = repository.extensions().first()
        val dbByPkg = dbExtensions.associateBy { it.pkg }

        withContext(Dispatchers.IO) {
            for ((info, source) in infos) {
                val existing = dbByPkg[info.pkg]
                val row = InstalledExtension(
                    pkg = info.pkg,
                    provider = info.provider,
                    name = info.name,
                    file = loader.extsDir()
                        .listFiles()
                        ?.firstOrNull { it.extension == "apk" && it.name.startsWith("${info.pkg}-") }
                        ?.absolutePath
                        ?: "",
                    versionCode = info.versionCode,
                    versionName = info.versionName,
                    libVersion = info.libVersion.toString(),
                    sha256 = "",
                    sourceIds = listOf(source.id),
                    // The APK is the truth about everything except this.
                    enabled = existing?.enabled != false,
                )
                if (row != existing) repository.upsert(row)
            }
            val loadedPkgs = infos.keys.map { it.pkg }.toSet()
            dbExtensions.filter { it.pkg !in loadedPkgs }.forEach { repository.delete(it.pkg) }
        }

        knownSources.clear()
        val fresh = repository.extensions().first().filter { it.enabled }
        val sourcesByPkg = infos.entries.associate { it.key.pkg to it.value }
        for (ext in fresh) {
            sourcesByPkg[ext.pkg]?.let { knownSources[it.id] = it }
        }
        _sourcesFlow.value = knownSources.values.toList()
    }

    fun getById(sourceId: Long): LoadedSource? = knownSources[sourceId]
}

/** The one rule for "there is an update": the index has a higher version code. */
fun List<AvailableExtension>.hasUpdateFor(extension: InstalledExtension): Boolean =
    any { it.pkg == extension.pkg && it.versionCode > extension.versionCode }
