package dev.achmad.finbox.theme.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import kotlin.math.abs
import kotlinx.coroutines.launch

/** The picker wheels: an odd row count, so one of them sits under the selection band. */
private val WheelItemHeight = 44.dp
private const val WheelItems = 5
private val WheelHeight = WheelItemHeight * WheelItems

/** Month and year as two wheels, applied together. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthYearPickerSheet(
    selected: YearMonth,
    range: ClosedRange<YearMonth>,
    onDismiss: () -> Unit,
    onSelect: (YearMonth) -> Unit,
) {
    val years = remember(range) { (range.start.year..range.endInclusive.year).toList() }
    val locale = LocalLocale.current.platformLocale

    // The wheels are the state: what each one points at is the draft, and nothing leaves the sheet
    // until Apply — so spinning past a month doesn't drag the screen behind it along.
    val monthState = rememberLazyListState(selected.monthValue - 1)
    val yearState = rememberLazyListState(years.indexOf(selected.year).coerceAtLeast(0))
    val month by remember {
        derivedStateOf { Month.entries.getOrElse(monthState.centeredIndex) { selected.month } }
    }
    val year by remember {
        derivedStateOf { years.getOrElse(yearState.centeredIndex) { selected.year } }
    }
    val draft = YearMonth.of(year, month)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetScope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(text = "Select period", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier.height(WheelHeight),
                contentAlignment = Alignment.Center,
            ) {
                // Behind the wheels, marking the row they hand their value over in.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WheelItemHeight)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Wheel(
                        items = Month.entries,
                        state = monthState,
                        label = { it.getDisplayName(TextStyle.FULL, locale) },
                        // A month outside the data is still reachable, so the wheel doesn't fight
                        // the spin — it just can't be applied.
                        enabled = { YearMonth.of(year, it) in range },
                    )
                    Wheel(
                        items = years,
                        state = yearState,
                        label = { it.toString() },
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    sheetScope
                        .launch { sheetState.hide() }
                        .invokeOnCompletion { onSelect(draft) }
                },
                enabled = draft in range,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apply")
            }
        }
    }
}

/** One wheel: snaps to the row under the band, and dims everything that isn't there. */
@Composable
private fun <T> RowScope.Wheel(
    items: List<T>,
    state: LazyListState,
    label: (T) -> String,
    enabled: (T) -> Boolean = { true },
) {
    val scope = rememberCoroutineScope()
    val centered by remember { derivedStateOf { state.centeredIndex } }
    LazyColumn(
        state = state,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        // Half a wheel of empty space at each end, so the first and last row can reach the band.
        contentPadding = PaddingValues(vertical = WheelItemHeight * (WheelItems / 2)),
        flingBehavior = rememberSnapFlingBehavior(state),
    ) {
        itemsIndexed(items) { index, item ->
            Text(
                text = label(item),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                color = if (enabled(item)) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheelItemHeight)
                    .clickable { scope.launch { state.animateScrollToItem(index) } }
                    .wrapContentHeight()
                    .alpha(if (index == centered) 1f else 0.4f),
            )
        }
    }
}

/** Whichever row sits closest to the middle of the viewport — what the wheel points at. */
private val LazyListState.centeredIndex: Int
    get() {
        val info = layoutInfo
        val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
        return info.visibleItemsInfo
            .minByOrNull { abs(it.offset + it.size / 2 - middle) }
            ?.index
            ?: firstVisibleItemIndex
    }
