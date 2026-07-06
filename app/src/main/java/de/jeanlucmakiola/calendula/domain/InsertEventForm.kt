package de.jeanlucmakiola.calendula.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Build a prefilled [EventForm] from an `ACTION_INSERT` intent's extras (issue
 * #30). External apps and widgets (e.g. the Todo Agenda widget) launch the
 * calendar this way to create a new event, passing the fields as
 * [android.provider.CalendarContract] extras. Any field the intent omits falls
 * back to the same defaults the in-app "new event" uses — a timed start at the
 * next full hour and a one-hour duration. [EventForm.calendarId] is left null so
 * it resolves to the last-used / first-writable calendar, exactly like the
 * `.ics` single-event and plain new-event paths.
 *
 * Pure (no Android types) so it is unit-testable; the intent parsing that reads
 * the extras lives in `MainActivity.insertFormOrNull`.
 */
fun buildInsertEventForm(
    beginMillis: Long?,
    endMillis: Long?,
    isAllDay: Boolean,
    title: String?,
    description: String?,
    location: String?,
    rrule: String?,
    zone: TimeZone,
    now: Instant,
): EventForm {
    val (start, end) = if (isAllDay) {
        // All-day provider times are UTC midnights with an exclusive end; show
        // the last covered day and keep placeholder wall-clock times in case the
        // user switches the event to timed (mirrors EventDetail.toEditForm).
        val startDate = beginMillis
            ?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date }
            ?: now.toLocalDateTime(zone).date
        val endDate = endMillis
            ?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date }
            ?.let { exclusive -> maxOf(startDate, LocalDate.fromEpochDays(exclusive.toEpochDays() - 1)) }
            ?: startDate
        LocalDateTime(startDate, LocalTime(9, 0)) to LocalDateTime(endDate, LocalTime(10, 0))
    } else {
        val startTime = beginMillis
            ?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone) }
            ?: nextFullHour(now, zone)
        val endTime = endMillis
            ?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone) }
            ?.takeIf { it >= startTime }
            ?: (startTime.toInstant(zone) + 1.hours).toLocalDateTime(zone)
        startTime to endTime
    }
    return EventForm(
        calendarId = null,
        title = title.orEmpty(),
        isAllDay = isAllDay,
        start = start,
        end = end,
        location = location.orEmpty(),
        description = description.orEmpty(),
        // Bare RRULE value (Events.RRULE convention); tolerate a leading "RRULE:"
        // some callers include.
        rrule = rrule?.removePrefix("RRULE:")?.takeIf { it.isNotBlank() },
    )
}

private fun nextFullHour(now: Instant, zone: TimeZone): LocalDateTime {
    val hourMillis = 3_600_000L
    val rounded = (now.toEpochMilliseconds() / hourMillis + 1) * hourMillis
    return Instant.fromEpochMilliseconds(rounded).toLocalDateTime(zone)
}
