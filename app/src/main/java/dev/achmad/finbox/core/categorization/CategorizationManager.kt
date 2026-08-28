package dev.achmad.finbox.core.categorization

import dev.achmad.data.model.ClassificationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dev.achmad.data.repository.ClassificationRunRepository
import kotlinx.coroutines.launch

/**
 * Owns the one classify pass that may be running.
 *
 * App-scoped rather than screen-scoped, so leaving the screen does not cancel a
 * backfill. Not a WorkManager job: a pass dies with the process, at a little
 * repeated work rather than lost work — the next run picks up what was left.
 */
class CategorizationManager(
    private val categorizer: TransactionCategorizer,
    private val runs: ClassificationRunRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    init {
        // Any run still marked RUNNING is one the process died under: this
        // object owns the only job there is and has not started one yet.
        scope.launch { runs.cancelStale() }
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val isRunning: Boolean get() = job?.isActive == true

    /** Starts a pass, or does nothing if one is already going. */
    fun start(
        scopeOf: ClassificationScope,
        ids: Set<String> = emptySet(),
        replaceManual: Boolean = false,
    ): Boolean {
        if (isRunning) return false
        _state.value = State.Running(0, 0)
        job = scope.launch {
            try {
                categorizer.classify(scopeOf, ids, replaceManual) { progress ->
                    _state.value = State.Running(progress.done, progress.total)
                }
                _state.value = State.Idle
            } catch (_: Throwable) {
                // The pass records its own outcome in the history; this flow only
                // drives a progress bar, and it is finished either way.
                _state.value = State.Idle
            }
        }
        return true
    }

    suspend fun cancel() {
        job?.cancelAndJoin()
        job = null
        _state.value = State.Idle
    }

    sealed interface State {
        object Idle : State

        /** Counted in signature groups, because one group can be a hundred rows. */
        data class Running(val done: Int, val total: Int) : State
    }
}
