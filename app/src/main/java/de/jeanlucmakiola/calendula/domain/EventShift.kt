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
 * An unparseable pinned id falls back to the device, like the write path does.
 */
fun EventForm.resolvedZone(deviceZone: TimeZone): TimeZone =
    timezone?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: deviceZone

/**
 * The form moved so it starts at [newStart], keeping its length. The wall-clock
 * values are re-derived in the event's own zone, not the device's, so dragging a
 * pinned event still means what the event means.
 *
 * The **instant** duration is preserved, not the wall-clock span: a recurring
 * event's length travels to the provider as `DURATION`, so preserving wall clock
 * would rewrite a whole series' length whenever a shift crosses a DST boundary.
 *
 * All-day events carry placeholder times and are date-anchored — move them with
 * [shiftedByDays] instead; this returns them untouched.
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
 * The form moved [days] calendar days, keeping its time of day and its length.
 * Both ends move, so a multi-day event keeps its span rather than collapsing to
 * a single day; an all-day event's placeholder times ride along untouched.
 */
fun EventForm.shiftedByDays(days: Int): EventForm {
    if (days == 0) return this
    return copy(
        start = LocalDateTime(start.date.plus(days, DateTimeUnit.DAY), start.time),
        end = LocalDateTime(end.date.plus(days, DateTimeUnit.DAY), end.time),
    )
}
