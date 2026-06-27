package de.jeanlucmakiola.calendula.ui.agenda

import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.FailureReason
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** One calendar day with at least one event, for the agenda list. */
data class AgendaDay(
    val date: LocalDate,
    /** Events on this day, all-day first then ascending by start time. */
    val events: List<EventInstance>,
)

/**
 * Group flat [instances] into forward-looking [AgendaDay]s (only days that
 * actually carry events). An event that began before [anchor] (ongoing or
 * multi-day) is clamped to the anchor day so it still surfaces on top. Within a
 * day, all-day events sort first, then ascending by start time, then title.
 *
 * Shared by the Agenda screen and the agenda home-screen widget so both group
 * and order identically.
 */
fun groupAgendaDays(
    anchor: LocalDate,
    instances: List<EventInstance>,
    zone: TimeZone,
): List<AgendaDay> =
    instances
        .groupBy { it.start.toLocalDateTime(zone).date.coerceAtLeast(anchor) }
        .toSortedMap()
        .map { (date, dayEvents) ->
            AgendaDay(
                date = date,
                events = dayEvents.sortedWith(
                    compareByDescending<EventInstance> { it.isAllDay }
                        .thenBy { it.start }
                        .thenBy { it.title },
                ),
            )
        }

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
        /** The range currently in effect — the saved default or a session override. */
        val range: AgendaRange,
        /** True when [range] is a temporary in-view override of the saved default. */
        val rangeIsOverride: Boolean,
    ) : AgendaUiState
}
