package dev.achmad.finbox.features.settings.categorize

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.model.ClassificationResult
import dev.achmad.data.model.ClassificationRun
import dev.achmad.data.repository.ClassificationRunRepository
import dev.achmad.finbox.util.koin.inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One run's decisions, as it recorded them. */
class ClassificationRunScreenModel(
    private val runId: Long,
    private val runs: ClassificationRunRepository = inject(),
) : ScreenModel {

    val results: StateFlow<List<ClassificationResult>> = runs.results(runId)
        .stateIn(screenModelScope, SharingStarted.Eagerly, emptyList())

    private val _run = MutableStateFlow<ClassificationRun?>(null)
    val run: StateFlow<ClassificationRun?> = _run.asStateFlow()

    init {
        // A run still going keeps writing rows, and the results flow carries
        // those on its own; only the header totals are a snapshot, and they are
        // refreshed when the list changes rather than polled.
        screenModelScope.launch {
            results.collect { _run.value = runs.getById(runId) }
        }
    }
}
