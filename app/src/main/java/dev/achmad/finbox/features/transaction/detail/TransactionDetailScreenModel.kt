package dev.achmad.finbox.features.transaction.detail

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.model.Transaction
import dev.achmad.data.repository.TransactionRepository
import dev.achmad.finbox.core.parser.ParserManager
import dev.achmad.finbox.parser.TransactionType
import dev.achmad.finbox.util.koin.inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.collections.orEmpty

class TransactionDetailScreenModel(
    private val id: String,
    private val repository: TransactionRepository = inject(),
    parserManager: ParserManager = inject(),
) : ScreenModel {

    /**
     * Null until the read comes back, and null again once the row is deleted — the query
     * skips deleted rows, so the screen leaves on its own however the delete happened.
     */
    val transaction: StateFlow<Transaction?> = repository.transactions()
        .map { all -> all.firstOrNull { it.id == id } }
        .stateIn(screenModelScope, SharingStarted.Eagerly, null)

    /**
     * What the type picker offers: the types declared by the parser that read this row.
     * Empty while the registry loads, and empty for a row whose parser is gone — the
     * picker then only offers what the row already has.
     */
    val types: StateFlow<List<TransactionType>> =
        combine(transaction, parserManager.parsersFlow) { transaction, parsers ->
            parsers.firstOrNull { it.id == transaction?.parserId }?.types().orEmpty()
        }.stateIn(screenModelScope, SharingStarted.Eagerly, emptyList())

    fun save(edited: Transaction) {
        screenModelScope.launch { repository.update(edited) }
    }

    fun delete() {
        screenModelScope.launch { repository.delete(id) }
    }
}