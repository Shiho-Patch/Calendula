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
 * State for the continuous style (#38): a vertical stream of *self-contained*
 * months rather than one undifferentiated run of weeks. Each month is keyed by
 * absolute month index and carries only its own days — the boundary week keeps
 * its seven columns so the grid geometry never shifts, but the neighbour month's
 * dates are clipped out of it (see `clipWeekToMonth`) and render as blanks.
 *
 * [monthsByIndex] holds only the loaded window; indices outside it render as
 * placeholders until the window catches up.
 */
sealed interface ContinuousMonthUiState {
    data object Loading : ContinuousMonthUiState
    data class Failure(val reason: FailureReason) : ContinuousMonthUiState
    data class Success(
        val today: LocalDate,
        val monthsByIndex: Map<Int, List<MonthWeek>>,
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
