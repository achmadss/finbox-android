package dev.achmad.finbox.features.transaction.list

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import dev.achmad.finbox.core.parser.LoadedSource
import dev.achmad.finbox.features.account.list.AccountsScreen
import dev.achmad.finbox.features.parser.list.ParsersScreen
import dev.achmad.finbox.features.settings.SettingsScreen
import dev.achmad.finbox.theme.AppTheme
import dev.achmad.finbox.theme.components.AppBar
import dev.achmad.finbox.theme.components.MonthYearPickerSheet
import dev.achmad.finbox.theme.components.VerticalFastScroller
import dev.achmad.finbox.theme.components.rememberSpringOverscrollEffect
import dev.achmad.finbox.util.formatter.formatAmount
import dev.achmad.finbox.util.formatter.formatDay
import dev.achmad.finbox.util.formatter.formatMonthName
import dev.achmad.finbox.util.formatter.formatMonthYear
import dev.achmad.finbox.util.formatter.formatTime
import dev.achmad.finbox.util.ui.rememberUse24HourClock
import dev.achmad.finbox.util.formatter.toLocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalLocale
import dev.achmad.finbox.features.transaction.detail.TransactionDetailScreen
import kotlin.collections.get
import kotlin.time.Duration.Companion.milliseconds

/** How far the month labels drift over a full swipe. */
private val SwipeDrift = 24.dp

/** Inset around the transaction list, shared with the scrollbar so the thumb spans it. */
private val ListPadding = 16.dp

object TransactionsScreen : Screen {
    private fun readResolve(): Any = TransactionsScreen

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { TransactionsScreenModel() }
        val monthly by model.monthly.collectAsState()
        val months by model.months.collectAsState()
        val month by model.month.collectAsState()
        val latest by model.latest.collectAsState()
        val loading by model.loading.collectAsState()
        val filter by model.filter.collectAsState()
        val accounts by model.accounts.collectAsState()
        val sources by model.sources.collectAsState()
        val parserUpdates by model.parserUpdates.collectAsState()

        TransactionsScreenContent(
            monthly = monthly,
            months = months,
            month = month,
            latest = latest,
            loading = loading,
            filter = filter,
            accounts = accounts,
            sources = sources,
            parserUpdates = parserUpdates,
            onRefresh = model::refresh,
            onMonthChange = model::setMonth,
            onFilterChange = model::setFilter,
            onOpenTransaction = { navigator.push(TransactionDetailScreen(it.id)) },
            onOpenAccounts = { navigator.push(AccountsScreen) },
            onOpenParsers = { navigator.push(ParsersScreen) },
            onOpenSettings = { navigator.push(SettingsScreen) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionsScreenContent(
    monthly: Map<YearMonth, List<Transaction>>,
    months: List<YearMonth>,
    month: YearMonth,
    /** The month the screen opens on — the newest one with data. */
    latest: YearMonth,
    loading: Boolean,
    filter: TransactionFilter,
    accounts: List<EmailAccount>,
    sources: List<LoadedSource>,
    /** Parsers with a newer build in the repo index. */
    parserUpdates: Int = 0,
    onRefresh: () -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onFilterChange: (TransactionFilter) -> Unit,
    onOpenTransaction: (Transaction) -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenParsers: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showFilterBottomSheet by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }

    // Keyed on the kind alone, not on the source it was parsed by: a source id carries the
    // parser's version, so rows written by an older build would otherwise lose their name.
    // ponytail: two parsers declaring the same key show one name — per-source if that lands.
    // Empty until the registry loads, and empty for a kind a parser dropped, so the rows
    // fall through to the description as before.
    val kindNames = remember(sources) {
        sources.flatMap { it.kinds }.associate { it.key to it.name }
    }
    // By id here, because that is all a transaction stores. A row parsed by an earlier build of
    // a parser is filed under that build's source id and so goes unnamed, same as the filter.
    val sourceNames = remember(sources) { sources.associate { it.id to it.name } }

    if (showMonthPicker) {
        MonthYearPickerSheet(
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
        TransactionsFilterSheet(
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
                title = "Transactions",
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
                        title = "Parsers",
                        icon = Icons.Outlined.Extension,
                        badge = parserUpdates,
                        onClick = onOpenParsers,
                    ),
                    AppBar.OverflowAction(
                        title = "Settings",
                        icon = Icons.Outlined.Settings,
                        onClick = onOpenSettings,
                    ),
                ),
            )
        }
    ) { contentPadding ->
        // Seeded from the current load, not from `true`: the model outlives a trip to another
        // screen, so coming back finds the data already there and should show it straight away.
        // Held a moment past the load, so the spinner doesn't blink in and straight back out.
        val showSpinner by produceState(loading, loading) {
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
                // pass later, so indexing the new list with the old index names the neighboring
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
                    // current page pointing at its neighbor's data until the effect below
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
                        grouped = filter.sort == TransactionSort.DATE,
                        kindNames = kindNames,
                        sourceNames = sourceNames,
                        onOpenTransaction = onOpenTransaction,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthPage(
    transactions: List<Transaction>,
    filtered: Boolean,
    grouped: Boolean,
    kindNames: Map<String, String>,
    sourceNames: Map<Long, String>,
    onOpenTransaction: (Transaction) -> Unit,
) {
    // Both directions, because a month is not only what left: pay lands here too,
    // and a total that ignored it would disagree with the rows under it.
    val (spent, earned) = remember(transactions) {
        var out = 0L
        var income = 0L
        transactions.forEach { transaction ->
            val amount = transaction.amount ?: 0L
            // Same rule the rows use for their sign: only an expense counts as
            // money out, so a row whose parser left the type unset lands with
            // income rather than silently against the wrong side of the total.
            if (transaction.type == TransactionType.EXPENSE) out += amount else income += amount
        }
        out to income
    }
    Column(modifier = Modifier.fillMaxSize()) {
        // The headline is the difference the month made. Signed, like every row.
        Text(
            text = formatAmount(earned - spent),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            MonthTotal(label = "Out", amount = -spent)
            MonthTotal(label = "In", amount = earned)
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        when {
            transactions.isEmpty() -> EmptyTransactions(filtered = filtered)
            grouped -> TransactionList(transactions, kindNames, sourceNames, onOpenTransaction)
            else -> FlatTransactionList(transactions, kindNames, sourceNames, onOpenTransaction)
        }
    }
}

/** One half of the out/in pair under the month's total. */
@Composable
private fun RowScope.MonthTotal(label: String, amount: Long) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatAmount(amount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
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
private fun TransactionList(
    transactions: List<Transaction>,
    kindNames: Map<String, String>,
    sourceNames: Map<Long, String>,
    onOpenTransaction: (Transaction) -> Unit,
) {
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
                            val kind = kindNames[transaction.kind]
                            val source = sourceNames[transaction.sourceId] ?: "Unknown"
                            TransactionRow(transaction, kind, source) { onOpenTransaction(transaction) }
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
private fun FlatTransactionList(
    transactions: List<Transaction>,
    kindNames: Map<String, String>,
    sourceNames: Map<Long, String>,
    onOpenTransaction: (Transaction) -> Unit,
) {
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
                    val kind = kindNames[transaction.kind]
                    val source = sourceNames[transaction.sourceId] ?: "Unknown"
                    TransactionRow(transaction, kind, source, showDay = true) {
                        onOpenTransaction(transaction)
                    }
                    if (index != transactions.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(
    transaction: Transaction,
    kind: String?,
    source: String,
    showDay: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                // The parser's own word for it — "QRIS Payment", not the `QRIS` key stored
                // with the row.
                text = kind
                    ?: transaction.description
                    ?: transaction.merchant
                    ?: transaction.reference
                    ?: "Unknown",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = buildList {
                add(source)
            }
            Text(
                text = subtitle.joinToString(" • "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        val use24Hour = rememberUse24HourClock()
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatAmount(transaction.signedAmount, transaction.currency),
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (showDay) {
                    "${formatDay(transaction.timestamp)} ${formatTime(transaction.date, use24Hour)}"
                } else {
                    formatTime(transaction.date, use24Hour)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyTransactions(filtered: Boolean) {
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
                "Transactions parsed out of your email show up here."
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
private fun TransactionsScreenPreview() {
    val now = System.currentTimeMillis()
    fun sample(id: String, description: String, amount: Long, type: TransactionType, at: Long) =
        Transaction(
            accountId = "a",
            sourceId = 1L,
            emailMessageId = "m$id",
            index = 0,
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
        TransactionsScreenContent(
            monthly = mapOf(month to transactions),
            months = listOf(month.minusMonths(1), month),
            month = month,
            latest = month,
            loading = false,
            filter = TransactionFilter(),
            accounts = emptyList(),
            sources = emptyList(),
            onRefresh = {},
            onMonthChange = {},
            onFilterChange = {},
            onOpenTransaction = {},
            onOpenAccounts = {},
            onOpenParsers = {},
            onOpenSettings = {},
        )
    }
}
