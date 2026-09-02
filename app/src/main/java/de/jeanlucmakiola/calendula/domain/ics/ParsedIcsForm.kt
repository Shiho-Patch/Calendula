package de.jeanlucmakiola.calendula.domain.ics

import de.jeanlucmakiola.calendula.domain.EventForm
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Prefill the create form from a single parsed `.ics` event (the "open one
 * event" path). [calendarId] is left null so the form preselects the last-used
 * calendar, exactly like a fresh create — the user confirms the target and
 * reviews everything before saving. Mirrors `EventDetail.toEditForm`'s all-day
 * handling (provider all-day times are UTC midnights with an exclusive end).
 *
 * [ParsedIcsEvent.color] is deliberately not carried over: picking the calendar
 * is the first thing this form asks for, and that clears any colour (a raw ARGB
 * is invalid on a calendar whose account publishes a palette). Bulk import,
 * where the target is known up front, does keep it.
 */
fun ParsedIcsEvent.toEventForm(zone: TimeZone): EventForm {
    val (start, end) = if (isAllDay) {
        val startDate = this.start.toLocalDateTime(TimeZone.UTC).date
        val endExclusive = this.end.toLocalDateTime(TimeZone.UTC).date
        val endDate = maxOf(startDate, LocalDate.fromEpochDays(endExclusive.toEpochDays() - 1))
        LocalDateTime(startDate, LocalTime(9, 0)) to LocalDateTime(endDate, LocalTime(10, 0))
    } else {
        this.start.toLocalDateTime(zone) to this.end.toLocalDateTime(zone)
    }
    return EventForm(
        calendarId = null,
        title = summary,
        isAllDay = isAllDay,
        start = start,
        end = end,
        location = location.orEmpty(),
        description = description.orEmpty(),
        reminders = semanticReminderMinutes(),
        availability = availability,
        rrule = recurrenceRule?.removePrefix("RRULE:")?.takeIf { it.isNotBlank() },
    )
}
