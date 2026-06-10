package de.jeanlucmakiola.calendula.ui.month

import de.jeanlucmakiola.calendula.domain.FailureReason
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * Per-day aggregation surfaced to the month grid. We only need
 *   - the total event count (drives the optional "+N" indicator), and
 *   - up to three calendar colors for the dot row.
 *
 * The day cell never holds full event objects — the detail sheet pulls those
 * lazily.
 */
data class DayCellData(
    val count: Int,
    val swatches: List<Int>,
)

sealed interface MonthUiState {
    data object Loading : MonthUiState
    data class Failure(val reason: FailureReason) : MonthUiState
    data class Success(
        val month: YearMonth,
        val today: LocalDate,
        val cells: Map<LocalDate, DayCellData>,
    ) : MonthUiState
}
