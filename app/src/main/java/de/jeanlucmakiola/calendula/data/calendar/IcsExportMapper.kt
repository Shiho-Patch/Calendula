package de.jeanlucmakiola.calendula.data.calendar

import de.jeanlucmakiola.calendula.domain.EventStatus
import de.jeanlucmakiola.calendula.domain.ics.IcsEvent
import de.jeanlucmakiola.calendula.domain.ics.deriveIcsUid

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

// Android's calendar provider (and Calendula's own writes) use the non-standard
// single-unit forms P<n>S / P<n>D / P<n>W — seconds without the RFC-required
// leading T. Matched first; anything else falls through to the general grammar.
private val DURATION_SINGLE_UNIT = Regex("""([+-]?)P(\d+)([WDS])""")
private val DURATION_GENERAL =
    Regex("""([+-]?)P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?""")

/**
 * Milliseconds of a DURATION (`P1D`, `P3600S`, `PT1H30M`, `P1W`, …). Calendula
 * only ever writes `P<days>D` / `P<seconds>S`, but the parser also handles the
 * general RFC 5545 grammar so a foreign row on a local calendar still
 * round-trips. Unparseable input is treated as zero.
 */
internal fun parseRfc2445DurationMillis(duration: String?): Long {
    if (duration.isNullOrBlank()) return 0L
    val s = duration.trim()

    DURATION_SINGLE_UNIT.matchEntire(s)?.let { m ->
        val unitSeconds = when (m.groupValues[3]) {
            "W" -> 7L * 24 * 60 * 60
            "D" -> 24L * 60 * 60
            else -> 1L // S
        }
        return m.signum() * m.groupValues[2].toLong() * unitSeconds * 1_000L
    }

    val m = DURATION_GENERAL.matchEntire(s) ?: return 0L
    val weeks = m.groupValues[2].toLongOrNull() ?: 0L
    val days = m.groupValues[3].toLongOrNull() ?: 0L
    val hours = m.groupValues[4].toLongOrNull() ?: 0L
    val minutes = m.groupValues[5].toLongOrNull() ?: 0L
    val seconds = m.groupValues[6].toLongOrNull() ?: 0L
    val totalSeconds = ((((weeks * 7 + days) * 24 + hours) * 60 + minutes) * 60 + seconds)
    return m.signum() * totalSeconds * 1_000L
}

/** Sign carried by group 1 (`-` → -1, otherwise +1) of a duration match. */
private fun MatchResult.signum(): Long = if (groupValues[1] == "-") -1L else 1L
