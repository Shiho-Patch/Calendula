package de.jeanlucmakiola.calendula.ui.agenda

import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.FailureReason
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * The zone the event's calendar dates live in. Timed events are resolved in the
 * device [zone]; all-day events live at UTC midnights with an exclusive end, so
 * resolving them anywhere but UTC shifts the boundaries — east of UTC that leaks
 * a one-day event onto its next day. Matches the Week view and detail card.
 */
private fun EventInstance.dateZone(zone: TimeZone): TimeZone =
    if (isAllDay) TimeZone.UTC else zone

/** The first calendar day this event occupies. */
fun EventInstance.spanFirstDay(zone: TimeZone): LocalDate =
    start.toLocalDateTime(dateZone(zone)).date

/**
 * The last calendar day this event actually occupies. An event ending exactly at
 * midnight (all-day events end at the exclusive next-midnight) does not reach
 * into that boundary day, so resolve the instant just before [end].
 */
fun EventInstance.spanLastDay(zone: TimeZone): LocalDate {
    val lastInstant = if (end > start) end - 1.milliseconds else start
    return lastInstant.toLocalDateTime(dateZone(zone)).date
}

/** Whether this event occupies more than one calendar day in [zone]. */
fun EventInstance.spansMultipleDays(zone: TimeZone): Boolean =
    spanFirstDay(zone) != spanLastDay(zone)

/**
 * What an agenda row's time line should convey for an event on a given day —
 * the part of a multi-day span that [day] falls in. Pure and shared so the
 * agenda screen and the agenda widget label multi-day events identically; each
 * surface only formats the instants into its own locale/24h string.
 */
sealed interface AgendaTimeLabel {
    /** An all-day event, or a whole in-between day of a multi-day span. */
    data object AllDay : AgendaTimeLabel
    /** The first day of a multi-day timed event: when it begins. */
    data class Starts(val start: Instant) : AgendaTimeLabel
    /** The last day of a multi-day timed event: when it ends. */
    data class Ends(val end: Instant) : AgendaTimeLabel
    /** A single-day timed event: its start–end range. */
    data class Range(val start: Instant, val end: Instant) : AgendaTimeLabel
}

/** The [AgendaTimeLabel] for [event] as it appears on [day], resolved in [zone]. */
fun agendaTimeLabel(event: EventInstance, day: LocalDate, zone: TimeZone): AgendaTimeLabel {
    if (event.isAllDay) return AgendaTimeLabel.AllDay
    val firstDay = event.spanFirstDay(zone)
    val lastDay = event.spanLastDay(zone)
    return when {
        firstDay == lastDay -> AgendaTimeLabel.Range(event.start, event.end)
        day <= firstDay -> AgendaTimeLabel.Starts(event.start)
        day >= lastDay -> AgendaTimeLabel.Ends(event.end)
        else -> AgendaTimeLabel.AllDay // a full in-between day
    }
}

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
        val firstDay = instance.spanFirstDay(zone).coerceAtLeast(anchor)
        // Never below firstDay: an instance the query returned always surfaces on
        // at least its first visible day, even if its end resolves earlier (e.g. a
        // zero-length or boundary instant) — otherwise the loop would drop it.
        val lastDay = instance.spanLastDay(zone).coerceAtMost(windowEnd).coerceAtLeast(firstDay)
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
