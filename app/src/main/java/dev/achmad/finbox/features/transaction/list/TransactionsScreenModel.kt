package dev.achmad.finbox.features.transaction.list

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.achmad.data.model.EmailAccount
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionCategory
import dev.achmad.data.model.TransactionDirection
import dev.achmad.data.repository.AccountRepository
import dev.achmad.data.repository.TransactionRepository
import dev.achmad.finbox.core.extension.ExtensionManager
import dev.achmad.finbox.core.extension.LoadedExtension
import dev.achmad.finbox.core.update.transaction.TransactionUpdateManager
import dev.achmad.finbox.util.formatter.toLocalDate
import dev.achmad.finbox.util.koin.inject
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
    val directions: Set<TransactionDirection> = emptySet(),
    val extensionIds: Set<Long> = emptySet(),
    val accountIds: Set<String> = emptySet(),
    val sort: TransactionSort = TransactionSort.DATE,
    val descending: Boolean = true,
) {
    val isActive: Boolean
        get() = directions.isNotEmpty() ||
            extensionIds.isNotEmpty() ||
            accountIds.isNotEmpty() ||
            sort != TransactionSort.DATE ||
            !descending

    fun applyTo(transactions: List<Transaction>): List<Transaction> {
        val kept = transactions.filter {
            (directions.isEmpty() || it.direction in directions) &&
                (extensionIds.isEmpty() || it.extensionId in extensionIds) &&
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
    private val transactionRepository: TransactionRepository = inject(),
    accountRepository: AccountRepository = inject(),
    private val extensionManager: ExtensionManager = inject(),
    private val transactionUpdateManager: TransactionUpdateManager = inject()
) : ScreenModel {

    /** Extensions currently loaded — what a transaction's `extensionId` points at. */
    val extensions: StateFlow<List<LoadedExtension>> = extensionManager.extensionsFlow

    val extensionUpdates: StateFlow<Int> = extensionManager.updatesCount

    init {
        // The registry only fills on reload, and opening straight onto this screen means nothing
        // has done that yet — the filter sheet would offer no extensions. Idempotent and cheap.
        screenModelScope.launch { extensionManager.reload() }
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
        screenModelScope.launch { transactionUpdateManager.runNow() }
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

    private val _selected = MutableStateFlow<Set<String>>(emptySet())

    /**
     * The rows a bulk action would touch. Empty means the list is in its normal
     * state — there is no separate "selection mode" flag to keep in step with it.
     */
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    fun toggleSelection(id: String) {
        _selected.value = _selected.value.let { if (id in it) it - id else it + id }
    }

    /** Everything currently on screen, which is one month and whatever the filter kept. */
    fun selectAll(ids: Collection<String>) {
        _selected.value = _selected.value + ids
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    /**
     * Files the selection by hand and drops it, so a second bulk write cannot
     * land on rows the user has stopped looking at.
     */
    fun setCategory(category: TransactionCategory) {
        val ids = _selected.value
        if (ids.isEmpty()) return
        _selected.value = emptySet()
        screenModelScope.launch { transactionRepository.setCategoryByUser(ids, category) }
    }
}
