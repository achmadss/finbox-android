package dev.achmad.finbox.features.transaction.detail

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionCategory
import dev.achmad.data.model.signature
import dev.achmad.data.repository.TransactionRepository
import dev.achmad.finbox.core.extension.ExtensionManager
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
    extensionManager: ExtensionManager = inject(),
) : ScreenModel {

    /**
     * Null until the read comes back, and null again once the row is deleted — the query
     * skips deleted rows, so the screen leaves on its own however the delete happened.
     */
    val transaction: StateFlow<Transaction?> = repository.transactions()
        .map { all -> all.firstOrNull { it.id == id } }
        .stateIn(screenModelScope, SharingStarted.Eagerly, null)

    fun save(edited: Transaction) {
        screenModelScope.launch { repository.update(edited) }
    }

    /**
     * Rows a classifier would read identically to [transaction], minus those already
     * filed under its category — including them would name a number that means nothing.
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