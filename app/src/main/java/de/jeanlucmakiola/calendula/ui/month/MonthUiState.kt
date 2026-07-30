package de.jeanlucmakiola.calendula.ui.month

import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.FailureReason
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth

/**
 * An all-day or multi-day event laid out as one connected horizontal bar across
 * a week row, [startCol]..[endCol], stacked on [lane] so overlapping spans don't
 * collide. Mirrors the week view's [de.jeanlucmakiola.calendula.ui.week.AllDaySpan]
 * but adds clip flags so a bar that started in an earlier week (or runs into a
 * later one) drops its rounded cap on that side.
 */
data class MonthSpan(
    val event: EventInstance,
    val startCol: Int,
    val endCol: Int,
    val lane: Int,
    val continuesLeft: Boolean,
    val continuesRight: Boolean,
)

/**
 * One week row of the grid with its events resolved for rendering.
 *
 *  - [spans] are the all-day/multi-day bars, lanes already assigned for the row.
 *  - [timedByDay] holds the single-day timed events per date, sorted by start;
 *    these render as filled pills beneath the bar lanes in their own cell.
 *  - [countByDay] is the total number of events touching each date (bars + pills),
 *    so the cell can compute the "+N" overflow once the visible-row cap is known.
 */
data class MonthWeek(
    val days: List<LocalDate>,
    val spans: List<MonthSpan>,
    val timedByDay: Map<LocalDate, List<EventInstance>>,
    val countByDay: Map<LocalDate, Int>,
)

/**
 * The events occupying each lane of [day] — column [col] of this week — in lane
 * order, capped at [laneCap] lanes.
 *
 * This is the same seating [spans]/[timedByDay] get when the paged grid draws a
 * week: bars keep the lane the row layout gave them, and the day's timed events
 * fill whatever slots are left, top-most first. Reading it here means the split
 * style's dots and the paged style's bars describe a day in the *same order*, so
 * dot _i_ and lane _i_ are the same event and one can morph into the other (#53).
 *
 * Deriving the dots independently — by distinct colour, as they first were —
 * left dot _i_ standing for no particular event, and quietly merged two events
 * that shared a calendar into a single dot.
 */
fun MonthWeek.laneEvents(col: Int, day: LocalDate, laneCap: Int): List<EventInstance> {
    val byLane = arrayOfNulls<EventInstance>(laneCap)
    spans.forEach { span ->
        if (span.lane < laneCap && col in span.startCol..span.endCol) {
            byLane[span.lane] = span.event
        }
    }
    val free = (0 until laneCap).filter { byLane[it] == null }
    timedByDay[day].orEmpty().take(free.size).forEachIndexed { i, event ->
        byLane[free[i]] = event
    }
    return byLane.filterNotNull()
}

/**
 * The events on [day] that [laneEvents] had no lane left for — the exact
 * complement of what it seats, in the same bars-then-pills order. Together the
 * two partition the day, so their sizes add up to [countByDay].
 *
 * The "+N" marker needs the events themselves, not just how many there are:
 * dimming a completed event is a per-event question (#79).
 */
fun MonthWeek.overflowEvents(col: Int, day: LocalDate, laneCap: Int): List<EventInstance> {
    val seatedLanes = spans.count { it.lane < laneCap && col in it.startCol..it.endCol }
    return buildList {
        spans.forEach { span ->
            if (span.lane >= laneCap && col in span.startCol..span.endCol) add(span.event)
        }
        addAll(timedByDay[day].orEmpty().drop(laneCap - seatedLanes))
    }
}

/**
 * State for the continuous style (#38): a vertical stream of *self-contained*
 * months rather than one undifferentiated run of weeks. Each month is keyed by
 * absolute month index and carries only its own days — the boundary week keeps
 * its seven columns so the grid geometry never shifts, but the neighbour month's
 * dates are clipped out of it (see `clipWeekToMonth`) and render as blanks.
 *
 * The same loaded window is served two ways, because the Dense style is the same
 * scroll with the seams taken out and it would be wasteful to query the provider
 * twice for one range:
 *
 *  - [monthsByIndex] — the block layout, keyed by absolute month index, each week
 *    clipped to its own month.
 *  - [weeksByIndex] — the flowing layout, keyed by absolute week index, boundary
 *    weeks left carrying both months.
 *
 * Both hold only the loaded window; indices outside it render as placeholders
 * until the window catches up.
 */
sealed interface ContinuousMonthUiState {
    data object Loading : ContinuousMonthUiState
    data class Failure(val reason: FailureReason) : ContinuousMonthUiState
    data class Success(
        val today: LocalDate,
        val monthsByIndex: Map<Int, List<MonthWeek>>,
        val weeksByIndex: Map<Int, MonthWeek>,
        val weekStart: DayOfWeek,
    ) : ContinuousMonthUiState
}

sealed interface MonthUiState {
    data object Loading : MonthUiState
    data class Failure(val reason: FailureReason) : MonthUiState

    /**
     * [weeks] is what the grid draws; [instancesByDay] is the same events keyed by
     * date and *uncapped*, so the split style's day pane can list everything on a
     * date without a second provider query — the month grid range already covers
     * it. All-day events sort first, then by start.
     */
    data class Success(
        val month: YearMonth,
        val today: LocalDate,
        val weeks: List<MonthWeek>,
        val instancesByDay: Map<LocalDate, List<EventInstance>> = emptyMap(),
        /**
         * Travels on the state rather than being read at the call site, for the
         * same reason the agenda does it: a file-level constant would be fixed
         * for the process lifetime and drift from the zone the events were laid
         * out in after a device time-zone change.
         */
        val zone: TimeZone = TimeZone.currentSystemDefault(),
    ) : MonthUiState
}
