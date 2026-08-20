package dev.achmad.finbox.core.parser

import android.content.Context
import dev.achmad.data.model.InstalledParser
import dev.achmad.data.repository.InstalledParserRepository
import dev.achmad.finbox.core.update.transaction.TransactionUpdateJob
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
import dev.achmad.finbox.core.preference.ParserKindPreference

/**
 * Orchestrates installed parsers:
 * - loads APKs from disk ([reload])
 * - keeps the `installed_parser` DB table in sync
 * - fetches the repo index for the available/update lists
 * - runs installs and updates ([install], [update])
 */
class ParserManager(
    private val context: Context,
    private val loader: ParserLoader,
    private val installer: ParserInstaller,
    private val index: ParserIndex,
    private val repository: InstalledParserRepository,
    private val kindPreference: ParserKindPreference,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val mutex = Mutex()

    /** pkg -> parser metadata for successfully loaded parsers. */
    val installedInfo: MutableStateFlow<Map<String, InstalledParserInfo>> = MutableStateFlow(emptyMap())

    /** Load failures by file name, surfaced in the UI. */
    val loadErrors: MutableStateFlow<Map<String, String>> = MutableStateFlow(emptyMap())

    /** Repo index entries fetched from finbox-parser. */
    val available: MutableStateFlow<List<AvailableParser>> = MutableStateFlow(emptyList())

    val installed: StateFlow<List<InstalledParser>> = repository.parsers()
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

    /** Set by an install that landed, cleared by whichever one asks for the re-read. */
    private val reparseWanted = AtomicBoolean(false)

    /** sourceId -> loaded LoadedSource for enabled parsers. */
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
    private fun installParser(parser: AvailableParser): Flow<InstallStep> =
        installer.downloadAndInstall(parser)
            .onEach { if (it == InstallStep.Installed) reload() }

    /**
     * Downloads and installs [parser], reporting progress through [installSteps].
     *
     * Run here rather than from the screen that asked: an install outlives the row
     * it started from, and leaving that screen must not cancel a download halfway.
     * Only [cancelInstall] stops one.
     */
    fun install(parser: AvailableParser) {
        val pkg = parser.pkg
        installJobs.remove(pkg)?.cancel()
        // Before the job runs, so a retry stops reading as the failure it is
        // replacing — [installSteps] keeps an error until something supersedes it.
        _installSteps.update { it + (pkg to InstallStep.Pending) }
        installJobs[pkg] = scope.launch {
            var last = InstallStep.Idle
            installParser(parser)
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
            if (last == InstallStep.Installed) reparseWanted.set(true)
            // The last install of a batch speaks for all of them. Asking per parser only
            // gets the first request in — the rest arrive while that one runs and are
            // turned away, so the parsers behind them were never re-read.
            if (installJobs.isEmpty() && reparseWanted.getAndSet(false)) reparse()
        }
    }

    /** Installs the newer build the index has of [pkg]. */
    fun update(pkg: String) {
        val parser = available.value.firstOrNull { it.pkg == pkg } ?: return
        install(parser)
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
        // The app asked, not the user: a request turned down here is not worth a toast.
        TransactionUpdateJob.reparseNow(context, userInitiated = false)
    }

    /** Installed parsers the index has a newer build of. */
    fun pendingUpdates(): List<InstalledParser> =
        installed.value.filter { available.value.hasUpdateFor(it) }

    suspend fun remove(pkg: String) {
        installer.remove(pkg)
        repository.delete(pkg)
        // The switches are keyed by package and would otherwise outlive it,
        // quietly suppressing kinds if the same parser is installed again.
        kindPreference.clear(pkg)
        reload()
    }

    suspend fun setEnabled(pkg: String, enabled: Boolean) {
        repository.setEnabled(pkg, enabled)
        reload()
    }

    /** Reloads APKs from disk and resyncs the DB + in-memory source registry. */
    suspend fun reload() = mutex.withLock {
        val results = withContext(Dispatchers.IO) { loader.loadParsers() }

        val infos = mutableMapOf<InstalledParserInfo, LoadedSource>()
        val errors = mutableMapOf<String, String>()
        for (result in results) {
            when (result) {
                is LoadResult.Success -> infos[result.parser] = result.source
                is LoadResult.Error -> errors[result.file] = result.reason
            }
        }
        installedInfo.value = infos.entries.associate { it.key.pkg to it.key }
        loadErrors.value = errors

        val dbParsers = repository.parsers().first()
        val dbByPkg = dbParsers.associateBy { it.pkg }

        withContext(Dispatchers.IO) {
            for ((info, source) in infos) {
                val existing = dbByPkg[info.pkg]
                val row = InstalledParser(
                    pkg = info.pkg,
                    provider = info.provider,
                    name = info.name,
                    file = loader.parsersDir()
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
            dbParsers.filter { it.pkg !in loadedPkgs }.forEach { repository.delete(it.pkg) }
        }

        knownSources.clear()
        val fresh = repository.parsers().first().filter { it.enabled }
        val sourcesByPkg = infos.entries.associate { it.key.pkg to it.value }
        for (ext in fresh) {
            sourcesByPkg[ext.pkg]?.let { knownSources[it.id] = it }
        }
        _sourcesFlow.value = knownSources.values.toList()
    }

    fun getById(sourceId: Long): LoadedSource? = knownSources[sourceId]
}

/** The one rule for "there is an update": the index has a higher version code. */
fun List<AvailableParser>.hasUpdateFor(parser: InstalledParser): Boolean =
    any { it.pkg == parser.pkg && it.versionCode > parser.versionCode }
