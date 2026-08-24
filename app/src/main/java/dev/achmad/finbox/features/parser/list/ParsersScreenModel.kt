package dev.achmad.finbox.features.parser.list

import android.util.Log
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.model.InstalledParser
import dev.achmad.finbox.core.parser.AvailableParser
import dev.achmad.finbox.core.parser.ParserManager
import dev.achmad.finbox.core.parser.ParserUpdateNotifier
import dev.achmad.finbox.core.parser.InstallStep
import dev.achmad.finbox.core.update.transaction.TransactionUpdateManager
import dev.achmad.finbox.util.koin.inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class ParsersScreenModel(
    private val manager: ParserManager = inject(),
    private val parserUpdateNotifier: ParserUpdateNotifier = inject(),
    private val transactionUpdateManager: TransactionUpdateManager = inject(),
) : StateScreenModel<ParsersScreenModel.State>(State()) {

    init {
        screenModelScope.launch {
            combine(
                manager.installed,
                manager.available,
                manager.loadErrors,
                manager.installSteps,
            ) { installed, available, errors, downloads ->
                groupParsers(installed, available, errors, downloads)
            }.collect { fresh ->
                mutableState.update { fresh.copy(isRefreshing = it.isRefreshing) }
            }
        }

        screenModelScope.launch {
            // Already loaded if an update ran in this process; cheap and idempotent
            // if the screen is what opened first.
            manager.reload()
            runCatching { manager.refreshIndex() }
                .onFailure { Log.e("Parsers", "Parser index fetch failed", it) }
        }

        // Nothing left to update means the notification is stale, whoever cleared it.
        screenModelScope.launch {
            manager.updatesCount.collect { count ->
                if (count == 0) parserUpdateNotifier.dismiss()
            }
        }
    }

    fun refresh() {
        screenModelScope.launch {
            mutableState.update { it.copy(isRefreshing = true) }
            // The index is one small fetch, so the indicator would otherwise blink in
            // and straight back out with nothing read from it.
            delay(1.seconds)
            runCatching {
                manager.refreshIndex()
                manager.reload()
            }.onFailure { Log.e("Parsers", "Parser refresh failed", it) }
            mutableState.update { it.copy(isRefreshing = false) }
        }
    }

    // Installs run in the manager, not here: this screen model dies when the user
    // navigates away, and a download must not die with it.
    fun install(parser: AvailableParser) = manager.install(parser)

    fun update(pkg: String) = manager.update(pkg)

    fun updateAll() = state.value.updates.forEach { update(it.pkg) }

    fun cancelInstall(pkg: String) = manager.cancelInstall(pkg)

    fun setEnabled(pkg: String, enabled: Boolean) {
        screenModelScope.launch {
            manager.setEnabled(pkg, enabled)
            // A parser switched on reads the mail it has not tried yet. Handed to a
            // job, which outlives this screen.
            transactionUpdateManager.reparseNow()
        }
    }

    fun uninstall(pkg: String) {
        screenModelScope.launch { manager.remove(pkg) }
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val updates: List<ParserUiModel.Installed> = emptyList(),
        val installed: List<ParserUiModel.Installed> = emptyList(),
        val available: List<ParserUiModel.Available> = emptyList(),
        val errors: Map<String, String> = emptyMap(),
    ) {
        val isEmpty = updates.isEmpty() && installed.isEmpty() && available.isEmpty() && errors.isEmpty()
    }
}

/**
 * The three lists the screen draws, from the two the manager keeps.
 *
 * An installed parser with a newer entry in the index moves out of
 * "installed" and into "updates"; the index entry for something already
 * installed is not offered again.
 */
fun groupParsers(
    installed: List<InstalledParser>,
    available: List<AvailableParser>,
    errors: Map<String, String>,
    downloads: Map<String, InstallStep>,
): ParsersScreenModel.State {
    val installedItems = installed
        .map { parser ->
            ParserUiModel.Installed(
                parser = parser,
                update = available.firstOrNull {
                    it.pkg == parser.pkg && it.versionCode > parser.versionCode
                },
                installStep = downloads[parser.pkg] ?: InstallStep.Idle,
            )
        }
        .sortedBy { it.name.lowercase() }
    return ParsersScreenModel.State(
        isLoading = false,
        updates = installedItems.filter { it.update != null },
        installed = installedItems.filter { it.update == null },
        available = available
            .filter { entry -> installed.none { it.pkg == entry.pkg } }
            .map {
                ParserUiModel.Available(
                    parser = it,
                    installStep = downloads[it.pkg] ?: InstallStep.Idle,
                )
            }
            .sortedBy { it.name.lowercase() },
        errors = errors,
    )
}

sealed interface ParserUiModel {
    val pkg: String
    val name: String
    val provider: String
    val versionName: String
    val installStep: InstallStep

    val isRunning: Boolean get() = !installStep.isCompleted()

    data class Installed(
        val parser: InstalledParser,
        val update: AvailableParser?,
        override val installStep: InstallStep = InstallStep.Idle,
    ) : ParserUiModel {
        override val pkg get() = parser.pkg
        override val name get() = parser.name
        override val provider get() = parser.provider
        override val versionName get() = parser.versionName
        val enabled get() = parser.enabled
    }

    data class Available(
        val parser: AvailableParser,
        override val installStep: InstallStep = InstallStep.Idle,
    ) : ParserUiModel {
        override val pkg get() = parser.pkg
        override val name get() = parser.name
        override val provider get() = parser.provider
        override val versionName get() = parser.versionName
    }
}
