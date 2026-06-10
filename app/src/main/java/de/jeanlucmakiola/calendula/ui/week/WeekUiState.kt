package de.jeanlucmakiola.calendula.ui.week

import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.FailureReason
import kotlinx.datetime.LocalDate

/**
 * One timed event clipped to a single day and assigned a horizontal lane so
 * overlapping events render side-by-side (spec S2: "Overlap-Events nebeneinander
 * aufgelöst").
 *
 * @param startMin minutes from this day's midnight, clamped to [0, 1440]
 * @param endMin   minutes from this day's midnight, clamped to [startMin, 1440];
 *                 equal to [startMin] for instant events (render enforces a
 *                 minimum tap-target height)
 * @param lane     0-based column within [laneCount]
 * @param laneCount number of columns the event's overlap-cluster needs
 */
data class TimedBlock(
    val event: EventInstance,
    val startMin: Int,
    val endMin: Int,
    val lane: Int,
    val laneCount: Int,
)

/**
 * An all-day (or multi-day) event laid out as a single horizontal bar spanning
 * [startCol]..[endCol] of the visible week, stacked on row [lane] so overlapping
 * spans don't collide. A multi-day event is one connected bar — not one chip per
 * day.
 *
 * @param startCol first visible covered column, 0..6 (clamped to the week)
 * @param endCol   last visible covered column, 0..6, inclusive
 * @param lane     0-based stacking row
 */
data class AllDaySpan(
    val event: EventInstance,
    val startCol: Int,
    val endCol: Int,
    val lane: Int,
)

sealed interface WeekUiState {
    data object Loading : WeekUiState
    data class Failure(val reason: FailureReason) : WeekUiState
    data class Success(
        val weekStart: LocalDate,
        val today: LocalDate,
        /** The seven days of the week, [weekStart] first. */
        val days: List<LocalDate>,
        /** All-day/multi-day events as connected horizontal spans. */
        val allDaySpans: List<AllDaySpan>,
        /** Timed events, clipped to each day with lanes resolved. */
        val timedByDay: Map<LocalDate, List<TimedBlock>>,
    ) : WeekUiState
}
