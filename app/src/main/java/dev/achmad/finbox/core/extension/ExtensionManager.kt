package dev.achmad.finbox.core.extension

import dev.achmad.data.model.InstalledExtension
import dev.achmad.data.repository.InstalledExtensionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

    val hasUpdates: StateFlow<Boolean> = combine(installed, available) { installed, available ->
        installed.any { inst ->
            available.any { it.pkg == inst.pkg && it.versionCode > inst.versionCode }
        }
    }.stateIn(scope, SharingStarted.Eagerly, false)

    /** sourceId -> loaded LoadedSource for enabled extensions. */
    private val knownSources: LinkedHashMap<Long, LoadedSource> = LinkedHashMap()

    val sources: List<LoadedSource>
        get() = knownSources.values.toList()

    suspend fun refreshIndex() {
        available.value = index.fetch()
    }

    suspend fun install(extension: AvailableExtension) {
        installer.install(extension).getOrThrow()
        reload()
    }

    suspend fun updateAvailable(pkg: String) {
        val extension = available.value.firstOrNull { it.pkg == pkg } ?: return
        install(extension)
    }

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
                if (existing == null) {
                    repository.upsert(
                        InstalledExtension(
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
                            enabled = true,
                        ),
                    )
                } else if (existing.sourceIds != listOf(source.id)) {
                    repository.upsert(existing.copy(sourceIds = listOf(source.id)))
                }
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
    }

    fun getById(sourceId: Long): LoadedSource? = knownSources[sourceId]
}
