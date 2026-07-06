package de.jeanlucmakiola.calendula.data.calendar

import de.jeanlucmakiola.floret.time.toKotlinInstantFromEpochMillis
import de.jeanlucmakiola.calendula.domain.EventStatus
import de.jeanlucmakiola.calendula.domain.ics.IcsEvent
import de.jeanlucmakiola.calendula.domain.ics.deriveIcsUid
import de.jeanlucmakiola.calendula.domain.ics.parseRfc2445DurationMillis

/**
 * Map one Events row (read through [EventExportProjection]) into an [IcsEvent]
 * for backup. [reminderMinutes] are the row's raw provider reminder offsets and
 * [calendarName] the display name of its calendar (emitted as
 * `X-CALENDULA-CALENDAR`). Pure given a [ColumnReader] — JVM-tested with
 * MapColumnReader.
 */
internal fun ColumnReader.toIcsEvent(
    reminderMinutes: List<Int>,
    calendarName: String?,
): IcsEvent {
    val eventId = getLong(EventExportProjection.IDX_ID)
    val dtStart = getLong(EventExportProjection.IDX_DTSTART)
    val rrule = getString(EventExportProjection.IDX_RRULE)?.takeIf { it.isNotBlank() }

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
        isAllDay = getInt(EventExportProjection.IDX_ALL_DAY) != 0,
        zoneId = getString(EventExportProjection.IDX_EVENT_TIMEZONE)?.takeIf { it.isNotBlank() }
            ?: "UTC",
        recurrenceRule = rrule,
        location = getString(EventExportProjection.IDX_LOCATION),
        description = getString(EventExportProjection.IDX_DESCRIPTION),
        reminderMinutes = reminderMinutes,
        status = status,
        availability = mapAvailability(getInt(EventExportProjection.IDX_AVAILABILITY)),
        calendarName = calendarName,
    )
}

