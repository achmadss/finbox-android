package dev.achmad.finbox.features.transaction.detail

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionCategory
import dev.achmad.data.model.signature
import dev.achmad.data.repository.TransactionRepository
import dev.achmad.finbox.core.parser.ParserManager
import dev.achmad.finbox.parser.TransactionMethod
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
     * What the method picker offers: the methods declared by the parser that read this row.
     * Empty while the registry loads, and empty for a row whose parser is gone — the
     * picker then only offers what the row already has.
     */
    val methods: StateFlow<List<TransactionMethod>> =
        combine(transaction, parserManager.parsersFlow) { transaction, parsers ->
            parsers.firstOrNull { it.id == transaction?.parserId }?.methods().orEmpty()
        }.stateIn(screenModelScope, SharingStarted.Eagerly, emptyList())

    fun save(edited: Transaction) {
        screenModelScope.launch { repository.update(edited) }
    }

    /**
     * Rows a classifier would read identically to [transaction] and that are not
     * already filed under its category.
     *
     * What the "apply to similar" offer counts. Rows already carrying the
     * category are left out: offering to change forty when thirty-nine already
     * agree names a number that means nothing.
     */
    suspend fun similarTo(transaction: Transaction): List<Transaction> =
        repository.withSignature(transaction.signature(), excludingId = transaction.id)
            .filter { it.category != transaction.category }

    fun applyCategoryTo(ids: List<String>, category: TransactionCategory) {
        screenModelScope.launch { repository.setCategoryByUser(ids, category) }
    }

    fun delete() {
        screenModelScope.launch { repository.delete(id) }
    }
}