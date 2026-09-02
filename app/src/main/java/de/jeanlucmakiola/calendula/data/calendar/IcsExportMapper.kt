package de.jeanlucmakiola.calendula.data.calendar

import de.jeanlucmakiola.floret.time.toKotlinInstantFromEpochMillis
import de.jeanlucmakiola.calendula.domain.EventStatus
import de.jeanlucmakiola.calendula.domain.ics.IcsEvent
import de.jeanlucmakiola.calendula.domain.ics.deriveIcsUid
import de.jeanlucmakiola.calendula.domain.ics.parseRfc2445DurationMillis
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Map one Events row (read through [EventExportProjection]) into an [IcsEvent]
 * for backup. [reminderMinutes] are the row's raw provider reminder offsets and
 * [calendarName] the display name of its calendar (emitted as
 * `X-CALENDULA-CALENDAR`). Pure given a [ColumnReader] — JVM-tested with
 * MapColumnReader.
 *
 * An all-day row's raw offset has the firing time of day encoded into it and is
 * normally *negative* (see [AllDayReminderEncoding]); written out literally it
 * would be dropped as a trigger that fires after the event, losing the reminder.
 * [allDayReminderTimeMinutes] and [zone] decode it back to the whole-day lead
 * time the file should carry, which an import re-encodes for its own device.
 */
internal fun ColumnReader.toIcsEvent(
    reminderMinutes: List<Int>,
    calendarName: String?,
    allDayReminderTimeMinutes: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): IcsEvent {
    val eventId = getLong(EventExportProjection.IDX_ID)
    val dtStart = getLong(EventExportProjection.IDX_DTSTART)
    val rrule = getString(EventExportProjection.IDX_RRULE)?.takeIf { it.isNotBlank() }
    val isAllDay = getInt(EventExportProjection.IDX_ALL_DAY) != 0

    // Recurring rows store DURATION instead of DTEND; reconstruct the end from it
    // so the writer can render DTEND. A missing/blank both means a zero-length event.
    val end = when {
        !isNull(EventExportProjection.IDX_DTEND) -> getLong(EventExportProjection.IDX_DTEND)
        else -> dtStart + parseRfc2445DurationMillis(getString(EventExportProjection.IDX_DURATION))
    }

    // STATUS shares value 0 with TENTATIVE, so an absent column must read as Confirmed.
    val status = if (isNull(EventExportProjection.IDX_STATUS)) {
        EventStatus.Confirmed
    } else {
        mapEventStatus(getInt(EventExportProjection.IDX_STATUS))
    }

    return IcsEvent(
        uid = deriveIcsUid(getString(EventExportProjection.IDX_UID), eventId, dtStart),
        summary = getString(EventExportProjection.IDX_TITLE).orEmpty(),
        start = dtStart.toKotlinInstantFromEpochMillis(),
        end = end.toKotlinInstantFromEpochMillis(),
        isAllDay = isAllDay,
        zoneId = getString(EventExportProjection.IDX_EVENT_TIMEZONE)?.takeIf { it.isNotBlank() }
            ?: "UTC",
        recurrenceRule = rrule,
        exDates = if (rrule == null) {
            emptyList()
        } else {
            exportExDates(
                getString(EventExportProjection.IDX_EXDATE),
                isAllDay,
                getString(EventExportProjection.IDX_EVENT_TIMEZONE),
            )
        },
        location = getString(EventExportProjection.IDX_LOCATION),
        description = getString(EventExportProjection.IDX_DESCRIPTION),
        reminderMinutes = if (isAllDay) {
            val startDate = Instant.ofEpochMilli(dtStart).atZone(ZoneOffset.UTC).toLocalDate()
            reminderMinutes.map {
                fromProviderAllDayMinutes(it, startDate, zone, allDayReminderTimeMinutes)
            }
        } else {
            reminderMinutes
        },
        status = status,
        availability = mapAvailability(getInt(EventExportProjection.IDX_AVAILABILITY)),
        calendarName = calendarName,
    )
}

/**
 * The row's `EXDATE` as the writer wants it: one stamp per entry, empties
 * dropped.
 *
 * An all-day exclusion names a calendar day, and sync adapters are inconsistent
 * about whether they write it as a bare `yyyyMMdd` or pad it to a midnight
 * stamp; the time part is dropped so the exported value is the day either way.
 * Calendula's own writes ([buildOccurrenceExdateValues]) are already bare.
 *
 * A zoned exclusion arrives in AOSP's `RecurrenceSet` form, `<zone>;<stamps>`,
 * where the prefix names the zone the whole list is wall clock in. [IcsWriter]
 * emits one bare `EXDATE:` line, and a parameter belongs before that colon, not
 * inside the value — passed on as-is it would make the line unparseable. The
 * prefix is therefore taken off and its stamps resolved to UTC instants
 * ([canonicalExdateStamp], the same reading the write path does), the form every
 * reader accepts.
 *
 * Which zone that resolution uses is [exdateListZone]'s call, the same one the
 * write path makes: the `TZID=` when this device's tz database knows the name,
 * else the row's own [eventTimezone]. A stamp is never emitted floating just
 * because the prefix named a zone we couldn't place — against a `DTSTART;TZID=…`
 * line every reader would resolve it in its own zone and the exclusion would stop
 * matching its occurrence.
 */
internal fun exportExDates(
    exdate: String?,
    isAllDay: Boolean,
    eventTimezone: String? = null,
): List<String> {
    if (exdate.isNullOrBlank()) return emptyList()
    val zone = exdateListZone(exdate, eventTimezone)
    return exdate
        .withoutExdateParameters()
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { stamp ->
            if (isAllDay) {
                stamp.substringBefore('T')
            } else {
                canonicalExdateStamp(stamp, isAllDay = false, listZone = zone) ?: stamp
            }
        }
        .distinct()
}
