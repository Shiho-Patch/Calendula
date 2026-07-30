package de.jeanlucmakiola.calendula.widget.month

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jeanlucmakiola.calendula.widget.WidgetSize

/** Event rows (lanes) shown per week before the rest collapse into "+N". */
internal const val MAX_LANES = 3

/**
 * Every size the month widget draws from, resolved from the user's chosen
 * [WidgetSize].
 *
 * [columnWidth] is the load-bearing one. The grid lays its seven day columns out
 * at this fixed width and centres the result in whatever space the host gives it,
 * rather than dividing a launcher-reported width by seven (#103) — a reported
 * width that overshoots the real one used to push the last columns off the edge.
 * A fixed column also keeps a multi-day event a single connected bar
 * `columnWidth * n` wide, which is why the columns can't simply be weighted.
 */
internal data class MonthMetrics(
    val columnWidth: Dp,      // one day column
    val gridPadding: Dp,      // horizontal padding either side of the grid
    val laneHeight: Dp,       // one event-bar row
    val dayNumberHeight: Dp,  // day-number row, and the today circle's diameter
    val headerTitle: TextUnit,
    val weekday: TextUnit,    // narrow weekday initials
    val dayNumber: TextUnit,
    val eventTitle: TextUnit, // title inside an event bar
    val overflow: TextUnit,   // the "+N" line
    val iconImage: Dp,        // header arrow/today glyph
    val iconBox: Dp,          // header action touch target
) {
    /** Width the seven columns plus their padding occupy — what a placement must fit. */
    val gridWidth: Dp get() = columnWidth * 7 + gridPadding * 2
}

/*
 * SMALL reproduces the widget's original constants so an existing widget is
 * unchanged until its owner turns the size up — except [columnWidth], which had
 * no fixed value before (it was derived from the reported width, at roughly 33dp
 * on a default 4-cell placement). 30dp sits just under that so the smallest step
 * still fits the narrowest placement the provider allows; see
 * `appwidget_info_month.xml`'s minResizeWidth, which is pinned against
 * SMALL.gridWidth by a test.
 */

private val SMALL_METRICS = MonthMetrics(
    columnWidth = 30.dp,
    gridPadding = 4.dp,
    laneHeight = 14.dp,
    dayNumberHeight = 18.dp,
    headerTitle = 15.sp,
    weekday = 11.sp,
    dayNumber = 11.sp,
    eventTitle = 9.sp,
    overflow = 9.sp,
    iconImage = 20.dp,
    iconBox = 40.dp,
)

private val MEDIUM_METRICS = MonthMetrics(
    columnWidth = 38.dp,
    gridPadding = 6.dp,
    laneHeight = 16.dp,
    dayNumberHeight = 22.dp,
    headerTitle = 17.sp,
    weekday = 12.sp,
    dayNumber = 13.sp,
    eventTitle = 10.sp,
    overflow = 10.sp,
    iconImage = 22.dp,
    iconBox = 44.dp,
)

private val LARGE_METRICS = MonthMetrics(
    columnWidth = 46.dp,
    gridPadding = 8.dp,
    laneHeight = 19.dp,
    dayNumberHeight = 26.dp,
    headerTitle = 19.sp,
    weekday = 13.sp,
    dayNumber = 15.sp,
    eventTitle = 11.sp,
    overflow = 11.sp,
    iconImage = 24.dp,
    iconBox = 48.dp,
)

private val EXTRA_LARGE_METRICS = MonthMetrics(
    columnWidth = 54.dp,
    gridPadding = 8.dp,
    laneHeight = 22.dp,
    dayNumberHeight = 30.dp,
    headerTitle = 22.sp,
    weekday = 14.sp,
    dayNumber = 17.sp,
    eventTitle = 12.sp,
    overflow = 12.sp,
    iconImage = 26.dp,
    iconBox = 52.dp,
)

/** Indexed by [WidgetSize.ordinal] so lookup allocates nothing per recomposition. */
private val MONTH_METRICS = listOf(
    SMALL_METRICS,
    MEDIUM_METRICS,
    LARGE_METRICS,
    EXTRA_LARGE_METRICS,
)

internal fun monthMetricsFor(size: WidgetSize): MonthMetrics = MONTH_METRICS[size.ordinal]
