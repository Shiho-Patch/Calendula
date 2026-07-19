package de.jeanlucmakiola.calendula.ui.agenda

import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.FailureReason
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds

/** One calendar day with at least one event, for the agenda list. */
data class AgendaDay(
    val date: LocalDate,
    /** Events on this day, all-day first then ascending by start time. */
    val events: List<EventInstance>,
)

/**
 * Group flat [instances] into forward-looking [AgendaDay]s (only days that
 * actually carry events). A multi-day event surfaces on *every* day it spans,
 * not just its first — clamped to [[anchor], [windowEnd]] so an event that began
 * before the window (ongoing) still lists from the anchor day, and one running
 * past the window stops at the last visible day. Within a day, all-day events
 * sort first, then ascending by start time, then title.
 *
 * Shared by the Agenda screen and the agenda home-screen widget so both group
 * and order identically.
 */
fun groupAgendaDays(
    anchor: LocalDate,
    windowEnd: LocalDate,
    instances: List<EventInstance>,
    zone: TimeZone,
): List<AgendaDay> {
    val byDay = sortedMapOf<LocalDate, MutableList<EventInstance>>()
    for (instance in instances) {
        val firstDay = instance.start.toLocalDateTime(zone).date.coerceAtLeast(anchor)
        // The last day the event actually occupies. An event ending exactly at
        // midnight (all-day events end at the exclusive next-midnight) does not
        // reach into that boundary day, so resolve the instant just before [end].
        val lastInstant = if (instance.end > instance.start) instance.end - 1.milliseconds else instance.start
        val lastDay = lastInstant.toLocalDateTime(zone).date.coerceAtMost(windowEnd)
        var day = firstDay
        while (day <= lastDay) {
            byDay.getOrPut(day) { mutableListOf() }.add(instance)
            day = day.plus(1, DateTimeUnit.DAY)
        }
    }
    return byDay.map { (date, dayEvents) ->
        AgendaDay(
            date = date,
            events = dayEvents.sortedWith(
                compareByDescending<EventInstance> { it.isAllDay }
                    .thenBy { it.start }
                    .thenBy { it.title },
            ),
        )
    }
}

/**
 * Ensure [today] surfaces as the first agenda day even when it carries no
 * (remaining) events, by prepending an empty-event [AgendaDay] the agenda widget
 * renders as a "nothing left today" placeholder. A no-op unless [enabled], and
 * when today already has its own day in [days]. Keeps today anchored at the top
 * so a glance tells today's events apart from a future day's (issue #35).
 */
fun anchorTodayIfMissing(
    days: List<AgendaDay>,
    today: LocalDate,
    enabled: Boolean,
): List<AgendaDay> =
    if (enabled && days.none { it.date == today }) {
        listOf(AgendaDay(today, emptyList())) + days
    } else {
        days
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
        /** Last day the current [range] covers (inclusive), for the range header. */
        val rangeEnd: LocalDate,
        /** Whether to show the top range bar — header + switcher (toggle, on by default). */
        val showRangeBar: Boolean,
    ) : AgendaUiState
}
