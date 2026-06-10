package de.jeanlucmakiola.calendula.ui.day

import de.jeanlucmakiola.calendula.domain.FailureReason
import de.jeanlucmakiola.calendula.ui.week.AllDaySpan
import de.jeanlucmakiola.calendula.ui.week.TimedBlock
import kotlinx.datetime.LocalDate

/**
 * The day view is a single-column slice of the week view (spec S3). It reuses the
 * week's [TimedBlock] and [AllDaySpan] layout primitives — for one day, all-day
 * spans collapse to a single column ([AllDaySpan.startCol] == [AllDaySpan.endCol]
 * == 0) and only their [AllDaySpan.lane] (vertical stacking) matters.
 */
sealed interface DayUiState {
    data object Loading : DayUiState
    data class Failure(val reason: FailureReason) : DayUiState
    data class Success(
        val date: LocalDate,
        val today: LocalDate,
        /** All-day/multi-day events covering this day, stacked by lane. */
        val allDay: List<AllDaySpan>,
        /** Timed events clipped to this day with overlap lanes resolved. */
        val timed: List<TimedBlock>,
    ) : DayUiState
}
