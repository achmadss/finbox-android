package dev.achmad.finbox.core.extension

import dev.achmad.data.model.InstalledExtension
import dev.achmad.data.repository.InstalledExtensionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Orchestrates installed extensions:
 * - loads APKs from disk ([reload])
 * - keeps the `installed_extension` DB table in sync
 * - fetches the repo index for the available/update lists
 */
class ExtensionManager(
    private val loader: ExtensionLoader,
    private val installer: ExtensionInstaller,
    private val index: ExtensionIndex,
    private val repository: InstalledExtensionRepository,
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
     * Dropping the collector cancels the install.
     */
    fun installExtension(extension: AvailableExtension): Flow<InstallStep> =
        installer.downloadAndInstall(extension)
            .onEach { if (it == InstallStep.Installed) reload() }

    fun updateExtension(pkg: String): Flow<InstallStep> {
        val extension = available.value.firstOrNull { it.pkg == pkg } ?: return emptyFlow()
        return installExtension(extension)
    }

    /** Pass or fail only, for callers with no row to report steps on. */
    suspend fun install(extension: AvailableExtension) {
        val last = installExtension(extension).last()
        if (last != InstallStep.Installed) throw IOException("Install failed for ${extension.pkg}")
    }

    /** Installed extensions the index has a newer build of. */
    fun pendingUpdates(): List<InstalledExtension> =
        installed.value.filter { available.value.hasUpdateFor(it) }

    suspend fun remove(pkg: String) {
        installer.remove(pkg)
        repository.delete(pkg)
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
