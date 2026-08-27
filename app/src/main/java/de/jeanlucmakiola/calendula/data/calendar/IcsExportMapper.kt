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

