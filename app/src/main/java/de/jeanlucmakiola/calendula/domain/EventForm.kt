package de.jeanlucmakiola.calendula.domain

import kotlinx.datetime.LocalDateTime

/**
 * User input for creating an event (and, from v1.3, editing one). Times are
 * wall-clock values in the device zone; the data layer translates them to
 * provider millis (all-day events normalise to UTC midnights there).
 */
data class EventForm(
    val calendarId: Long?,
    val title: String = "",
    val isAllDay: Boolean = false,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val location: String = "",
    val description: String = "",
)

enum class EventFormProblem {
    /** No target calendar — none picked and no writable calendar exists. */
    NoCalendar,
    EndBeforeStart,
}

/**
 * Validation; an empty set means the form can be saved. A blank title is
 * allowed (display falls back to "(No title)", matching the provider), and a
 * zero-length timed event is allowed (spec §8: instant events exist).
 */
fun EventForm.problems(): Set<EventFormProblem> = buildSet {
    if (calendarId == null) add(EventFormProblem.NoCalendar)
    val endsTooEarly = if (isAllDay) end.date < start.date else end < start
    if (endsTooEarly) add(EventFormProblem.EndBeforeStart)
}
