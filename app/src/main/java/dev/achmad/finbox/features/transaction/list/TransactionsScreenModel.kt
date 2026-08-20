package dev.achmad.finbox.features.transaction.list

import android.content.Context
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.model.EmailAccount
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionType
import dev.achmad.data.repository.AccountRepository
import dev.achmad.data.repository.TransactionRepository
import dev.achmad.finbox.core.parser.ParserManager
import dev.achmad.finbox.core.parser.LoadedSource
import dev.achmad.finbox.core.update.transaction.TransactionUpdateJob
import dev.achmad.finbox.util.formatter.toLocalDate
import dev.achmad.finbox.util.koin.inject
import dev.achmad.finbox.util.koin.injectAndroidContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

enum class TransactionSort { DATE, AMOUNT }

/** An empty set means "no restriction", so the default filter lets everything through. */
data class TransactionFilter(
    val types: Set<TransactionType> = emptySet(),
    val sourceIds: Set<Long> = emptySet(),
    val accountIds: Set<String> = emptySet(),
    val sort: TransactionSort = TransactionSort.DATE,
    val descending: Boolean = true,
) {
    val isActive: Boolean
        get() = types.isNotEmpty() ||
            sourceIds.isNotEmpty() ||
            accountIds.isNotEmpty() ||
            sort != TransactionSort.DATE ||
            !descending

    fun applyTo(transactions: List<Transaction>): List<Transaction> {
        val kept = transactions.filter {
            (types.isEmpty() || it.type in types) &&
                (sourceIds.isEmpty() || it.sourceId in sourceIds) &&
                (accountIds.isEmpty() || it.accountId in accountIds)
        }
        val sorted = when (sort) {
            TransactionSort.DATE -> kept.sortedBy { it.timestamp }
            // Nulls sort as 0, i.e. alongside the smallest amounts.
            TransactionSort.AMOUNT -> kept.sortedBy { it.amount ?: 0L }
        }
        return if (descending) sorted.asReversed() else sorted
    }
}

/**
 * Oldest month with data to the newest, gaps included, always covering [home] and [selected] so
 * there is somewhere to swipe back to.
 */
internal fun monthRange(
    data: Set<YearMonth>,
    home: YearMonth,
    selected: YearMonth,
): List<YearMonth> {
    val bounds = data + home + selected
    val last = bounds.max()
    return generateSequence(bounds.min()) { it.plusMonths(1).takeIf { next -> next <= last } }
        .toList()
}

class TransactionsScreenModel(
    transactionRepository: TransactionRepository = inject(),
    accountRepository: AccountRepository = inject(),
    private val parserManager: ParserManager = inject(),
    private val context: Context = injectAndroidContext()
) : ScreenModel {

    /** Parsers currently loaded — what a transaction's `sourceId` points at. */
    val sources: StateFlow<List<LoadedSource>> = parserManager.sourcesFlow

    /** Badge on the Parsers menu item. */
    val parserUpdates: StateFlow<Int> = parserManager.updatesCount

    init {
        // The registry only fills on reload, and opening straight onto this screen means nothing
        // has done that yet — the filter sheet would offer no parsers. Idempotent and cheap.
        screenModelScope.launch { parserManager.reload() }
    }

    private val picked = MutableStateFlow<YearMonth?>(null)

    private val _filter = MutableStateFlow(TransactionFilter())
    val filter: StateFlow<TransactionFilter> = _filter.asStateFlow()

    val accounts: StateFlow<List<EmailAccount>> = accountRepository.accounts()
        .stateIn(screenModelScope, SharingStarted.Eagerly, emptyList())

    /** Null until the first read comes back — the screen waits rather than guessing a month. */
    private val transactions: StateFlow<List<Transaction>?> = transactionRepository.transactions()
        .stateIn(screenModelScope, SharingStarted.Eagerly, null)

    val loading: StateFlow<Boolean> = transactions
        .map { it == null }
        .stateIn(screenModelScope, SharingStarted.Eagerly, true)

    /** Every month at once, so swiping to the next one has its data ready. */
    val monthly: StateFlow<Map<YearMonth, List<Transaction>>> = combine(
        transactions,
        _filter,
    ) { all, filter ->
        filter.applyTo(all.orEmpty()).groupBy { YearMonth.from(toLocalDate(it.timestamp)) }
    }.stateIn(screenModelScope, SharingStarted.Eagerly, emptyMap())

    /** Where the screen sits until you pick something else: the newest month with data. */
    val latest: StateFlow<YearMonth> = transactions
        .map { all ->
            all?.maxOfOrNull { YearMonth.from(toLocalDate(it.timestamp)) } ?: YearMonth.now()
        }
        .stateIn(screenModelScope, SharingStarted.Eagerly, YearMonth.now())

    val month: StateFlow<YearMonth> = combine(picked, latest) { picked, latest -> picked ?: latest }
        .stateIn(screenModelScope, SharingStarted.Eagerly, YearMonth.now())

    /** The months you can swipe through: bounded by all the data, not by what the filter kept. */
    val months: StateFlow<List<YearMonth>> = combine(
        transactions,
        month,
        latest,
    ) { all, selected, latest ->
        val data = all.orEmpty().mapTo(mutableSetOf()) { YearMonth.from(toLocalDate(it.timestamp)) }
        monthRange(data, latest, selected)
    }.stateIn(screenModelScope, SharingStarted.Eagerly, listOf(YearMonth.now()))

    /** The job outlives this screen; the app-wide banner reports how it goes. */
    fun refresh() {
        // Says so itself, with a toast, when another update is already running.
        screenModelScope.launch { TransactionUpdateJob.runNow(context) }
    }

    fun setMonth(month: YearMonth) {
        picked.value = month
    }

    fun setFilter(filter: TransactionFilter) {
        _filter.value = filter
    }

    fun resetFilter() {
        _filter.value = TransactionFilter()
    }
}
