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
import kotlinx.coroutines.launch

/**
 * Owns the one classify pass that may be running.
 *
 * App-scoped rather than screen-scoped, so leaving the screen does not cancel a
 * backfill, and only one at a time: two passes over the same rows would spend
 * twice the tokens to reach the same place.
 *
 * ponytail: not a WorkManager job, so a pass dies with the process. It is
 * dozens of requests, every batch is written as it lands, and the next run
 * picks up whatever was left — so the cost of dying is a little repeated work,
 * not lost work. Move it to a job if backfills ever get long enough that
 * someone would want to leave the app during one.
 */
class CategorizationManager(
    private val categorizer: TransactionCategorizer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val isRunning: Boolean get() = job?.isActive == true

    /**
     * Starts a pass, or does nothing if one is already going.
     *
     * @return false when it was turned away because one is already running.
     */
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
