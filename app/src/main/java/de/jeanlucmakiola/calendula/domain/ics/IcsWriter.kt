package de.jeanlucmakiola.calendula.domain.ics

import de.jeanlucmakiola.calendula.domain.Availability
import de.jeanlucmakiola.calendula.domain.EventStatus
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Default `PRODID` advertising the writer that produced the file. */
const val ICS_PROD_ID: String = "-//Jean-Luc Makiola//Calendula//EN"

/**
 * Hand-rolled RFC 5545 serialiser for the subset Calendula models. No iCal
 * library: we stay on `kotlinx-datetime` and own the output, exactly as
 * `domain/Recurrence.kt` owns RRULE. Pure and JVM-testable — the caller
 * supplies [dtStamp] (the export moment) so the writer never reads a clock.
 *
 * Timezone rule (see plan 05, decision 1):
 *  - all-day      → `VALUE=DATE`, no zone;
 *  - timed one-off → UTC instant with a `Z` suffix (an instant is an instant);
 *  - timed recurring → `TZID`-labelled local wall time, so the series stays
 *    anchored to wall-clock across DST. No `VTIMEZONE` block is emitted; import
 *    resolves the `TZID` against the OS tz database.
 */
class IcsWriter(private val prodId: String = ICS_PROD_ID) {

    fun writeCalendar(events: List<IcsEvent>, dtStamp: Instant): String {
        val lines = buildList {
            add("BEGIN:VCALENDAR")
            add("VERSION:2.0")
            add("PRODID:$prodId")
            add("CALSCALE:GREGORIAN")
            events.forEach { appendEvent(it, dtStamp) }
            add("END:VCALENDAR")
        }
        return lines.joinToString(ICS_CRLF, postfix = ICS_CRLF) { foldLine(it) }
    }

    private fun MutableList<String>.appendEvent(event: IcsEvent, dtStamp: Instant) {
        add("BEGIN:VEVENT")
        add("UID:${event.uid}")
        add("DTSTAMP:${utcStamp(dtStamp)}")
        add("SUMMARY:${escapeText(event.summary)}")
        appendTimes(event)
        event.recurrenceRule?.takeIf { it.isNotBlank() }
            ?.let { add("RRULE:${it.removePrefix("RRULE:")}") }
        event.location?.takeIf { it.isNotBlank() }
            ?.let { add("LOCATION:${escapeText(it)}") }
        event.description?.takeIf { it.isNotBlank() }
            ?.let { add("DESCRIPTION:${escapeText(it)}") }
        add("STATUS:${statusValue(event.status)}")
        add("TRANSP:${transpValue(event.availability)}")
        event.calendarName?.takeIf { it.isNotBlank() }
            ?.let { add("X-CALENDULA-CALENDAR:${escapeText(it)}") }
        event.reminderMinutes.filter { it >= 0 }.distinct().forEach { minutes ->
            appendAlarm(minutes, event.summary)
        }
        add("END:VEVENT")
    }

    private fun MutableList<String>.appendTimes(event: IcsEvent) = when {
        event.isAllDay -> {
            add("DTSTART;VALUE=DATE:${utcDate(event.start)}")
            add("DTEND;VALUE=DATE:${utcDate(event.end)}")
        }
        // Recurring: anchor to wall-clock in the event's own zone.
        event.recurrenceRule?.isNotBlank() == true -> {
            val zone = runCatching { TimeZone.of(event.zoneId) }.getOrNull()
            if (zone != null) {
                add("DTSTART;TZID=${event.zoneId}:${localStamp(event.start, zone)}")
                add("DTEND;TZID=${event.zoneId}:${localStamp(event.end, zone)}")
            } else {
                // Unknown zone id → fall back to plain UTC instants.
                add("DTSTART:${utcStamp(event.start)}")
                add("DTEND:${utcStamp(event.end)}")
            }
        }
        else -> {
            add("DTSTART:${utcStamp(event.start)}")
            add("DTEND:${utcStamp(event.end)}")
        }
    }

    private fun MutableList<String>.appendAlarm(minutes: Int, summary: String) {
        add("BEGIN:VALARM")
        add("ACTION:DISPLAY")
        add("DESCRIPTION:${escapeText(summary.ifBlank { "Reminder" })}")
        add("TRIGGER:${triggerValue(minutes)}")
        add("END:VALARM")
    }

    private companion object {
        fun statusValue(status: EventStatus): String = when (status) {
            EventStatus.Confirmed -> "CONFIRMED"
            EventStatus.Tentative -> "TENTATIVE"
            EventStatus.Cancelled -> "CANCELLED"
        }

        // iCal TRANSP is binary; only an explicitly free event is TRANSPARENT.
        fun transpValue(availability: Availability): String =
            if (availability == Availability.Free) "TRANSPARENT" else "OPAQUE"

        // A lead time of 0 fires at start (PT0M); anything positive is "before".
        fun triggerValue(minutes: Int): String =
            if (minutes <= 0) "PT0M" else "-PT${minutes}M"

        fun utcStamp(instant: Instant): String =
            basic(instant.toLocalDateTime(TimeZone.UTC)) + "Z"

        fun localStamp(instant: Instant, zone: TimeZone): String =
            basic(instant.toLocalDateTime(zone))

        fun utcDate(instant: Instant): String {
            val dt = instant.toLocalDateTime(TimeZone.UTC)
            return "%04d%02d%02d".format(dt.year, dt.month.number, dt.day)
        }

        fun basic(dt: LocalDateTime): String = "%04d%02d%02dT%02d%02d%02d".format(
            dt.year, dt.month.number, dt.day, dt.hour, dt.minute, dt.second,
        )
    }
}
