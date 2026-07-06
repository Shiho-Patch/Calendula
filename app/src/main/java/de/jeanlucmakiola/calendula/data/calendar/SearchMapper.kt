package de.jeanlucmakiola.calendula.data.calendar

import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.ics.parseRfc2445DurationMillis

/**
 * Map an Events-table row (a search hit) to an [EventInstance]. Unlike the
 * Instances query this reads the series master, so there is no instance id (the
 * event id stands in as the list key) and recurring rows carry DURATION instead
 * of DTEND — reconstruct the end the same way the `.ics` export does.
 */
internal fun ColumnReader.toSearchResult(): EventInstance? {
    // A pre-1970 series anchor is a legitimately negative epoch-millis DTSTART
    // (see EventDetailMapper / issue #34); drop only a genuinely absent one, so
    // long-running birthdays/anniversaries still surface in search.
    if (isNull(SearchProjection.IDX_DTSTART)) return null
    val dtStart = getLong(SearchProjection.IDX_DTSTART)
    val end = when {
        !isNull(SearchProjection.IDX_DTEND) -> getLong(SearchProjection.IDX_DTEND)
        else -> dtStart + parseRfc2445DurationMillis(getString(SearchProjection.IDX_DURATION))
    }.coerceAtLeast(dtStart)

    val rawTitle = getString(SearchProjection.IDX_TITLE)
    val title = if (rawTitle.isNullOrEmpty()) Fallbacks.UNTITLED_EVENT else rawTitle
    val color = if (isNull(SearchProjection.IDX_EVENT_COLOR)) {
        getInt(SearchProjection.IDX_CALENDAR_COLOR)
    } else {
        getInt(SearchProjection.IDX_EVENT_COLOR)
    }
    val eventId = getLong(SearchProjection.IDX_ID)

    return EventInstance(
        instanceId = eventId,
        eventId = eventId,
        calendarId = getLong(SearchProjection.IDX_CALENDAR_ID),
        title = title,
        start = dtStart.toKotlinInstantFromEpochMillis(),
        end = end.toKotlinInstantFromEpochMillis(),
        isAllDay = getInt(SearchProjection.IDX_ALL_DAY) != 0,
        color = color,
        location = getString(SearchProjection.IDX_LOCATION),
    )
}
