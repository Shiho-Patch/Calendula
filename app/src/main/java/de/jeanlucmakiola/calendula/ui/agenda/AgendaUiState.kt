package de.jeanlucmakiola.calendula.ui.agenda

import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.FailureReason
import kotlinx.datetime.LocalDate

/** One calendar day with at least one event, for the agenda list. */
data class AgendaDay(
    val date: LocalDate,
    /** Events on this day, all-day first then ascending by start time. */
    val events: List<EventInstance>,
)

/**
 * State for the Agenda view: a flat, forward-looking list of upcoming events
 * grouped by day (only days that actually have events appear).
 */
sealed interface AgendaUiState {
    data object Loading : AgendaUiState
    data class Failure(val reason: FailureReason) : AgendaUiState
    data class Success(
        /** First day of the loaded window (today, or a jumped-to date). */
        val anchor: LocalDate,
        val today: LocalDate,
        val days: List<AgendaDay>,
    ) : AgendaUiState
}
