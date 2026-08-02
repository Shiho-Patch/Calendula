package de.jeanlucmakiola.calendula.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The zone this form's wall-clock times mean, matching what the data layer
 * resolves them in at write time: the form's own pinned zone, else [deviceZone].
 */
fun EventForm.resolvedZone(deviceZone: TimeZone): TimeZone =
    timezone?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: deviceZone

/**
 * The form moved so it starts at [newStart], keeping its **instant** duration —
 * a recurring event's length travels to the provider as `DURATION`, so keeping
 * wall clock instead would rewrite the series' length across a DST boundary.
 *
 * All-day events are date-anchored — use [shiftedByDays]; this returns them
 * untouched.
 */
fun EventForm.shiftedTo(newStart: Instant, deviceZone: TimeZone): EventForm {
    if (isAllDay) return this
    val zone = resolvedZone(deviceZone)
    val span = end.toInstant(zone) - start.toInstant(zone)
    return copy(
        start = newStart.toLocalDateTime(zone),
        end = (newStart + span).toLocalDateTime(zone),
    )
}

/**
 * The form moved [days] calendar days, keeping its time of day and its span.
 * All-day events are pure date arithmetic; a timed one preserves its **instant**
 * duration, for the same reason [shiftedTo] does.
 */
fun EventForm.shiftedByDays(days: Int, deviceZone: TimeZone): EventForm {
    if (days == 0) return this
    val newStart = LocalDateTime(start.date.plus(days, DateTimeUnit.DAY), start.time)
    if (isAllDay) {
        return copy(
            start = newStart,
            end = LocalDateTime(end.date.plus(days, DateTimeUnit.DAY), end.time),
        )
    }
    val zone = resolvedZone(deviceZone)
    val span = end.toInstant(zone) - start.toInstant(zone)
    return copy(start = newStart, end = (newStart.toInstant(zone) + span).toLocalDateTime(zone))
}
