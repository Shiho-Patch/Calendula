package de.jeanlucmakiola.calendula.ui.month

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.ui.common.ScaledViewPreview
import de.jeanlucmakiola.calendula.ui.common.sampleMonthEvents
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * A live, scaled-down Month view in a given [MonthViewStyle], for the settings
 * and onboarding choosers.
 *
 * The month, today's position and the week start are all real; only the events
 * are stand-ins. See [ScaledViewPreview] for how the real grid composables get
 * measured at phone size and shrunk into the card.
 */
@Composable
internal fun MonthStylePreview(
    style: MonthViewStyle,
    weekStart: DayOfWeek,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = remember(zone) { Clock.System.now().toLocalDateTime(zone).date }
    val sample = remember(today, weekStart, zone) { sampleMonthState(today, weekStart, zone) }

    ScaledViewPreview(height = height, modifier = modifier) {
        WeekdayHeader(weekStart = weekStart, showWeekNumbers = false)
        when (style) {
            MonthViewStyle.Paged -> MonthGrid(
                state = sample.month,
                showWeekNumbers = false,
                onOpenDay = {},
            )
            MonthViewStyle.Continuous -> ContinuousMonthGrid(
                state = sample.continuous,
                listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = itemIndexForMonth(
                        monthIndexOf(YearMonth(today.year, today.month)),
                    ),
                ),
                showWeekNumbers = false,
                onOpenDay = {},
            )
            MonthViewStyle.Dense -> DenseMonthGrid(
                state = sample.continuous,
                listState = rememberLazyListState(
                    // Start a week above today's, so the preview shows a stream
                    // running past the viewport rather than one beginning at its
                    // top edge.
                    initialFirstVisibleItemIndex = weekIndexOf(today, weekStart) - 1,
                ),
                showWeekNumbers = false,
                onOpenDay = {},
            )
            MonthViewStyle.Split -> {
                SplitMonthGrid(
                    state = sample.month,
                    selected = today,
                    showWeekNumbers = false,
                    onSelectDay = {},
                )
                SplitDayPane(
                    date = today,
                    today = today,
                    events = sample.month.instancesByDay[today].orEmpty(),
                    zone = zone,
                    onOpenDay = {},
                    onEventClick = {},
                    onCreateEvent = {},
                    modifier = Modifier.fillMaxWidth().height(SPLIT_PANE_HEIGHT),
                )
            }
        }
    }
}

private val SPLIT_PANE_HEIGHT = 170.dp

private class SampleMonth(
    val month: MonthUiState.Success,
    val continuous: ContinuousMonthUiState.Success,
)

/**
 * A month's worth of stand-in events, laid out through the same
 * [layoutMonthWeeks] / [clipWeekToMonth] the live views use — so the preview
 * exercises the real span, lane and overflow logic rather than approximating it.
 */
private fun sampleMonthState(
    today: LocalDate,
    weekStart: DayOfWeek,
    zone: TimeZone,
): SampleMonth {
    val ym = YearMonth(today.year, today.month)
    val first = LocalDate(ym.year, ym.month, 1)
    val events = sampleMonthEvents(first, today, zone)

    val weeks = layoutMonthWeeks(ym, weekStart, events, zone)
    val month = MonthUiState.Success(
        month = ym,
        today = today,
        weeks = weeks,
        instancesByDay = instancesByDay(weeks.flatMap { it.days }, events, zone),
        zone = zone,
    )

    // The month either side of this one: enough for Continuous to show a block,
    // the whitespace under it and the next month's header coming up, and for
    // Dense to have somewhere to scroll in both directions.
    val centre = monthIndexOf(ym)
    val window = (centre - 1)..(centre + 1)
    val continuous = ContinuousMonthUiState.Success(
        today = today,
        monthsByIndex = window.associateWith { index ->
            val m = yearMonthForIndex(index)
            layoutMonthWeeks(m, weekStart, events, zone).map { clipWeekToMonth(it, m) }
        },
        weeksByIndex = weekWindowFor(window, weekStart).associateWith { index ->
            val days = (0 until 7).map {
                weekStartForIndex(index, weekStart).plus(it, DateTimeUnit.DAY)
            }
            layoutCalendarWeek(days, events, zone)
        },
        weekStart = weekStart,
    )
    return SampleMonth(month, continuous)
}
