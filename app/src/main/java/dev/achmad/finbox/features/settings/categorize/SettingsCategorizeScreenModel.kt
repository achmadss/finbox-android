package dev.achmad.finbox.features.settings.categorize

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.model.CategorySource
import dev.achmad.data.model.ClassificationRun
import dev.achmad.data.model.ClassificationScope
import dev.achmad.data.repository.ClassificationRunRepository
import dev.achmad.data.repository.TransactionRepository
import dev.achmad.finbox.core.categorization.CategorizationManager
import dev.achmad.finbox.core.categorization.TransactionCategorizer
import dev.achmad.finbox.core.llm.TransactionClassifier
import dev.achmad.finbox.util.koin.inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsCategorizeScreenModel(
    private val categorizer: TransactionCategorizer = inject(),
    private val manager: CategorizationManager = inject(),
    private val classifier: TransactionClassifier = inject(),
    private val transactions: TransactionRepository = inject(),
    runRepository: ClassificationRunRepository = inject(),
) : ScreenModel {

    val runs: StateFlow<List<ClassificationRun>> = runRepository.runs()
        .stateIn(screenModelScope, SharingStarted.Eagerly, emptyList())

    val progress: StateFlow<CategorizationManager.State> = manager.state

    private val _estimate = MutableStateFlow<TransactionCategorizer.Estimate?>(null)
    val estimate: StateFlow<TransactionCategorizer.Estimate?> = _estimate.asStateFlow()

    private val _mine = MutableStateFlow(0)

    /** How many rows the user filed themselves — what "redo everything" would overwrite. */
    val mine: StateFlow<Int> = _mine.asStateFlow()

    val hasProvider: Boolean get() = classifier.isConfigured()

    init {
        refresh()
        screenModelScope.launch {
            // A run left RUNNING is one the process died under; nothing will
            // finish it, and showing it as ongoing forever would be a lie.
            runRepository.cancelStale()
        }
    }

    fun refresh() {
        screenModelScope.launch {
            _estimate.value = categorizer.estimate(ClassificationScope.UNCATEGORIZED)
            _mine.value = transactions.all()
                .count { !it.deleted && it.categorySource == CategorySource.USER }
        }
    }

    fun start() {
        manager.start(ClassificationScope.UNCATEGORIZED)
        watch()
    }

    fun redoEverything(replaceManual: Boolean) {
        manager.start(ClassificationScope.ALL, replaceManual = replaceManual)
        watch()
    }

    fun cancel() {
        screenModelScope.launch { manager.cancel() }
    }

    /** The estimate is stale the moment a run finishes, so recount when it does. */
    private fun watch() {
        screenModelScope.launch {
            manager.state.collect { state ->
                if (state is CategorizationManager.State.Idle) {
                    refresh()
                    return@collect
                }
            }
        }
    }
}
