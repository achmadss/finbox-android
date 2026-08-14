package dev.achmad.finbox.features.expenses

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.achmad.data.model.EmailAccount
import dev.achmad.data.model.Transaction
import dev.achmad.data.model.TransactionType
import dev.achmad.finbox.core.extension.LoadedSource
import dev.achmad.finbox.features.accounts.AccountsScreen
import dev.achmad.finbox.features.extensions.ExtensionsScreen
import dev.achmad.finbox.theme.AppTheme
import dev.achmad.finbox.theme.components.AppBar
import dev.achmad.finbox.theme.components.VerticalFastScroller
import dev.achmad.finbox.theme.components.rememberSpringOverscrollEffect
import dev.achmad.finbox.util.formatter.formatAmount
import dev.achmad.finbox.util.formatter.formatDay
import dev.achmad.finbox.util.formatter.formatMonthName
import dev.achmad.finbox.util.formatter.formatMonthYear
import dev.achmad.finbox.util.formatter.formatTime
import dev.achmad.finbox.util.formatter.toLocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalLocale
import kotlin.time.Duration.Companion.milliseconds

/** How far the month labels drift over a full swipe. */
private val SwipeDrift = 24.dp

/** Inset around the transaction list, shared with the scrollbar so the thumb spans it. */
private val ListPadding = 16.dp

object ExpensesScreen : Screen {
    private fun readResolve(): Any = ExpensesScreen

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { ExpensesScreenModel() }
        val monthly by model.monthly.collectAsState()
        val months by model.months.collectAsState()
        val month by model.month.collectAsState()
        val latest by model.latest.collectAsState()
        val loading by model.loading.collectAsState()
        val filter by model.filter.collectAsState()
        val accounts by model.accounts.collectAsState()
        val sources by model.sources.collectAsState()
        val extensionUpdates by model.extensionUpdates.collectAsState()

        ExpensesScreenContent(
            monthly = monthly,
            months = months,
            month = month,
            latest = latest,
            loading = loading,
            filter = filter,
            accounts = accounts,
            sources = sources,
            extensionUpdates = extensionUpdates,
            onRefresh = model::refresh,
            onMonthChange = model::setMonth,
            onFilterChange = model::setFilter,
            onOpenAccounts = { navigator.push(AccountsScreen) },
            onOpenExtensions = { navigator.push(ExtensionsScreen) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpensesScreenContent(
    monthly: Map<YearMonth, List<Transaction>>,
    months: List<YearMonth>,
    month: YearMonth,
    /** The month the screen opens on — the newest one with data. */
    latest: YearMonth,
    loading: Boolean,
    filter: ExpenseFilter,
    accounts: List<EmailAccount>,
    sources: List<LoadedSource>,
    /** Extensions with a newer build in the repo index. */
    extensionUpdates: Int = 0,
    onRefresh: () -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onFilterChange: (ExpenseFilter) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenExtensions: () -> Unit,
) {
    var showFilterBottomSheet by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }

    if (showMonthPicker) {
        MonthYearPickerDialog(
            selected = month,
            // The pager's months are exactly the ones with data, so they bound the picker too.
            range = (months.firstOrNull() ?: month)..(months.lastOrNull() ?: month),
            onDismiss = { showMonthPicker = false },
            onSelect = {
                onMonthChange(it)
                showMonthPicker = false
            },
        )
    }

    if (showFilterBottomSheet) {
        ExpensesFilterSheet(
            filter = filter,
            accounts = accounts,
            sources = sources,
            onFilterChange = onFilterChange,
            onDismiss = { showFilterBottomSheet = false },
        )
    }

    Scaffold(
        topBar = {
            AppBar(
                modifier = Modifier.dropShadow(RectangleShape, Shadow(2.dp)),
                title = "Expenses",
                actions = listOf(
                    AppBar.Action(
                        title = "Filter",
                        icon = Icons.Outlined.FilterList,
                        iconTint = Color.Yellow.takeIf { filter.isActive },
                        onClick = { showFilterBottomSheet = true },
                    ),
                    // TODO feed the accounts badge a real count (unread accounts)
                    AppBar.OverflowAction(
                        title = "Accounts",
                        icon = Icons.Outlined.AccountCircle,
                        onClick = onOpenAccounts,
                    ),
                    AppBar.OverflowAction(
                        title = "Extensions",
                        icon = Icons.Outlined.Extension,
                        badge = extensionUpdates,
                        onClick = onOpenExtensions,
                    ),
                    AppBar.OverflowAction(
                        title = "Settings",
                        icon = Icons.Outlined.Settings,
                        onClick = { /* TODO settings screen */ },
                    ),
                ),
            )
        }
    ) { contentPadding ->
        val inspectionMode = LocalInspectionMode.current
        // Held a moment past the load, so the spinner doesn't blink in and straight back out.
        val showSpinner by produceState(!inspectionMode, loading) {
            if (loading) {
                value = true
            } else {
                delay(500.milliseconds)
                value = false
            }
        }
        if (showSpinner) {
            // Nothing here is right until the data arrives: the month, the range, the page the
            // pager sits on. Wait instead of showing a guess and correcting it a frame later.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val scope = rememberCoroutineScope()
        var refreshing by remember { mutableStateOf(false) }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                onRefresh()
                // The update runs long past this screen and the banner reports it, so the
                // indicator is only here to acknowledge the pull.
                scope.launch {
                    refreshing = true
                    delay(1000.milliseconds)
                    refreshing = false
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val page = months.indexOf(month).coerceAtLeast(0)
                val pagerState = rememberPagerState(initialPage = page, pageCount = { months.size })
                val latestMonths by rememberUpdatedState(months)
                val latestMonth by rememberUpdatedState(month)
                // Label from the pager, not from the selected month. currentPage flips halfway
                // through a drag — under the fade — while the selection only catches up once the
                // swipe settles, which would leave the old name showing as it lights back up.
                //
                // Kept as a month and only rewritten when the pager moves. An import inserting an
                // older month shifts every index at once and the pager re-anchors to its key a
                // pass later, so indexing the new list with the old index names the neighbouring
                // month for a frame — the flicker in the steps.
                var shown by remember { mutableStateOf(month) }
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }.collect { current ->
                        latestMonths.getOrNull(current)?.let { shown = it }
                    }
                }
                val shownPage = months.indexOf(shown).takeIf { it >= 0 } ?: page
                MonthSelector(
                    month = months.getOrNull(shownPage) ?: month,
                    latest = latest,
                    previous = months.getOrNull(shownPage - 1),
                    next = months.getOrNull(shownPage + 1),
                    // A lambda, not the value: the labels then follow the drag by redrawing their
                    // layer, without recomposing this screen on every frame of it.
                    swipe = { pagerState.currentPageOffsetFraction },
                    onSelect = onMonthChange,
                    onPick = { showMonthPicker = true },
                )
                // Keyed on the pager alone. Re-collecting would replay the settled index as a month
                // change, and while data loads that index keeps meaning a different month — which is
                // what made the screen skip through months on startup.
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.settledPage }.collect { settled ->
                        val landed = latestMonths.getOrNull(settled)
                        if (landed != null && landed != latestMonth) onMonthChange(landed)
                    }
                }
                // The other direction: put the pager on the selected month.
                var alignedTo by remember { mutableStateOf(month) }
                LaunchedEffect(page, months) {
                    if (pagerState.currentPage != page) {
                        // A month someone chose animates, however far away it is — a step, the
                        // picker, the jump button. Landing here with the same month means the
                        // list shifted underneath, so re-point without pretending it was a swipe.
                        if (month != alignedTo) {
                            pagerState.animateScrollToPage(page)
                        } else {
                            pagerState.scrollToPage(page)
                        }
                    }
                    alignedTo = month
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top,
                    // A month identifies its page. Without this the pager is anchored to a bare
                    // index, so an older month arriving at the front of the list leaves the
                    // current page pointing at its neighbour's data until the effect below
                    // corrects it — the flicker you see when an import writes.
                    key = { months[it] },
                    // Only the first and last month have anything left to overscroll, so the bounce
                    // is what tells you there is no more data that way.
                    overscrollEffect = rememberSpringOverscrollEffect(),
                ) { index ->
                    MonthPage(
                        transactions = monthly[months[index]].orEmpty(),
                        filtered = filter.isActive,
                        // Day headers only make sense while the list is in date order.
                        grouped = filter.sort == ExpenseSort.DATE,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthPage(transactions: List<Transaction>, filtered: Boolean, grouped: Boolean) {
    val spent = remember(transactions) {
        transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount ?: 0L }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = formatAmount(-spent),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(36.dp))
        HorizontalDivider()
        when {
            transactions.isEmpty() -> EmptyExpenses(filtered = filtered)
            grouped -> TransactionList(transactions)
            else -> FlatTransactionList(transactions)
        }
    }
}

@Composable
private fun MonthSelector(
    month: YearMonth,
    latest: YearMonth,
    previous: YearMonth?,
    next: YearMonth?,
    swipe: () -> Float,
    onSelect: (YearMonth) -> Unit,
    onPick: () -> Unit,
) {
    val atLatest = month == latest
    // The labels ride the swipe: they drift with the finger and hand over while invisible, so
    // the month never pops from one name to the next. Chevrons stay put — they are the target,
    // not the content. Read inside the layer block, so a drag redraws without recomposing.
    val ridesTheSwipe = Modifier.graphicsLayer {
        val fraction = swipe()
        translationX = -fraction * SwipeDrift.toPx()
        alpha = (1f - abs(fraction) * 2f).coerceIn(0f, 1f)
    }

    Column(
        modifier = Modifier.padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            MonthStep(
                target = previous,
                leading = true,
                labelMotion = ridesTheSwipe,
                onSelect = onSelect,
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onPick)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .then(ridesTheSwipe),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatMonthYear(month),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold.takeIf { atLatest },
                )
                Icon(
                    imageVector = Icons.Outlined.ArrowDropDown,
                    contentDescription = "Pick month",
                )
            }
            MonthStep(
                target = next,
                leading = false,
                labelMotion = ridesTheSwipe,
                onSelect = onSelect,
            )
        }
        // Stays in the layout while disabled, so the rows below don't jump as you swipe.
        val jumpAlpha by animateFloatAsState(
            targetValue = if (atLatest) 0f else 1f,
            label = "jumpToLatest",
        )
        TextButton(
            onClick = { onSelect(latest) },
            enabled = !atLatest,
            modifier = Modifier.alpha(jumpAlpha),
        ) {
            Text("Jump to latest")
        }
    }
}

/** Chevron and the month it steps to, as one target. Holds its space when there is no [target]. */
@Composable
private fun RowScope.MonthStep(
    target: YearMonth?,
    leading: Boolean,
    labelMotion: Modifier,
    onSelect: (YearMonth) -> Unit,
) {
    Box(
        modifier = Modifier.weight(1f),
        contentAlignment = if (leading) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        if (target == null) return@Box
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onSelect(target) }
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = "Previous month",
                )
            }
            Text(
                // No year: the centre label carries it.
                text = formatMonthName(target),
                modifier = labelMotion,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!leading) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = "Next month",
                )
            }
        }
    }
}

@Composable
private fun MonthYearPickerDialog(
    selected: YearMonth,
    range: ClosedRange<YearMonth>,
    onDismiss: () -> Unit,
    onSelect: (YearMonth) -> Unit,
) {
    var year by remember { mutableIntStateOf(selected.year) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { year-- }, enabled = year > range.start.year) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = "Previous year",
                    )
                }
                Text(text = year.toString(), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { year++ }, enabled = year < range.endInclusive.year) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "Next year",
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Month.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { entry ->
                            val yearMonth = YearMonth.of(year, entry)
                            FilterChip(
                                modifier = Modifier.weight(1f),
                                selected = yearMonth == selected,
                                enabled = yearMonth in range,
                                onClick = { onSelect(yearMonth) },
                                label = {
                                    Text(
                                        text = entry.getDisplayName(
                                            TextStyle.SHORT,
                                            LocalLocale.current.platformLocale,
                                        ),
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun TransactionList(transactions: List<Transaction>) {
    // groupBy keeps encounter order, so the model's sort survives the grouping.
    val days = remember(transactions) { transactions.groupBy { toLocalDate(it.timestamp) } }
    val listState = rememberLazyListState()
    VerticalFastScroller(
        listState = listState,
        modifier = Modifier.fillMaxSize(),
        topContentPadding = ListPadding,
        bottomContentPadding = ListPadding,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(ListPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            days.forEach { (day, dayTransactions) ->
                item(key = day) {
                    Text(
                        text = formatDay(day).uppercase(LocalLocale.current.platformLocale),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                item(key = "$day-items") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.inverseOnSurface),
                    ) {
                        dayTransactions.forEachIndexed { index, transaction ->
                            TransactionRow(transaction)
                            if (index != dayTransactions.lastIndex) {
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Sorted by something other than date, so the day goes on each row instead of a header. */
@Composable
private fun FlatTransactionList(transactions: List<Transaction>) {
    val listState = rememberLazyListState()
    VerticalFastScroller(
        listState = listState,
        modifier = Modifier.fillMaxSize(),
        topContentPadding = ListPadding,
        bottomContentPadding = ListPadding,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(ListPadding),
        ) {
            itemsIndexed(transactions, key = { _, it -> it.id }) { index, transaction ->
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.inverseOnSurface)) {
                    TransactionRow(transaction, showDay = true)
                    if (index != transactions.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(transaction: Transaction, showDay: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.description
                    ?: transaction.merchant
                    ?: transaction.reference
                    ?: "Unknown",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = transaction.type?.label ?: "Unknown",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatAmount(transaction.signedAmount, transaction.currency),
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (showDay) {
                    "${formatDay(transaction.timestamp)} ${formatTime(transaction.date)}"
                } else {
                    formatTime(transaction.date)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyExpenses(filtered: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = if (filtered) "Nothing matches the filter" else "Nothing this month",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = if (filtered) {
                "Loosen the filter, or pick another month."
            } else {
                "Transactions parsed out of your statements show up here."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// TODO strings.xml, along with the rest of the copy on this screen
internal val TransactionType.label: String
    get() = name.lowercase(Locale.getDefault()).replaceFirstChar { it.uppercase() }

@Preview
@Composable
private fun ExpensesScreenPreview() {
    val now = System.currentTimeMillis()
    fun sample(id: String, description: String, amount: Long, type: TransactionType, at: Long) =
        Transaction(
            id = id,
            accountId = "a",
            sourceId = 1L,
            emailMessageId = "m$id",
            threadId = null,
            reference = null,
            date = at,
            amount = amount,
            currency = "IDR",
            type = type,
            kind = null,
            category = null,
            description = description,
            merchant = description,
            createdAt = at,
            updatedAt = at,
            deleted = false,
        )
    val transactions = listOf(
        sample("1", "Kopi Kenangan", 24_000, TransactionType.EXPENSE, now),
        sample("2", "Tokopedia", 315_000, TransactionType.EXPENSE, now),
        sample("3", "Payroll", 12_500_000, TransactionType.INCOME, now - 86_400_000L),
        sample("4", "Transfer to savings", 1_000_000, TransactionType.EXPENSE, now - 86_400_000L),
    )
    val month = YearMonth.now()
    AppTheme {
        ExpensesScreenContent(
            monthly = mapOf(month to transactions),
            months = listOf(month.minusMonths(1), month),
            month = month,
            latest = month,
            loading = false,
            filter = ExpenseFilter(),
            accounts = emptyList(),
            sources = emptyList(),
            onRefresh = {},
            onMonthChange = {},
            onFilterChange = {},
            onOpenAccounts = {},
            onOpenExtensions = {},
        )
    }
}
