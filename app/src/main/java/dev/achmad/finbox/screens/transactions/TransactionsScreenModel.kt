package dev.achmad.finbox.screens.transactions

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.model.Transaction
import dev.achmad.data.repository.TransactionRepository
import dev.achmad.finbox.core.util.inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsScreenModel(
    private val repository: TransactionRepository = inject(),
) : ScreenModel {

    val query = MutableStateFlow("")

    // search("") falls back to the full list, so one flow covers both cases.
    val transactions: StateFlow<List<Transaction>> = query
        .flatMapLatest { repository.search(it) }
        .stateIn(screenModelScope, SharingStarted.Eagerly, emptyList())

    fun setQuery(q: String) {
        query.value = q
    }

    fun update(transaction: Transaction) {
        screenModelScope.launch { repository.update(transaction) }
    }

    fun delete(id: String) {
        screenModelScope.launch { repository.delete(id) }
    }
}
