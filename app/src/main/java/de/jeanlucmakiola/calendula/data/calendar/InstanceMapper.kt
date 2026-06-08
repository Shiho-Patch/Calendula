package de.jeanlucmakiola.calendula.data.calendar

import android.util.Log
import de.jeanlucmakiola.calendula.domain.EventInstance

private const val TAG = "InstanceMapper"

internal fun ColumnReader.toEventInstance(): EventInstance? {
    val begin = getLong(InstanceProjection.IDX_BEGIN)
    val end = getLong(InstanceProjection.IDX_END)

    if (begin < 0L) {
        Log.w(TAG, "Dropping row with negative begin=$begin")
        return null
    }
    if (end < begin) {
        Log.w(TAG, "Dropping row with end=$end < begin=$begin")
        return null
    }

    val rawTitle = getString(InstanceProjection.IDX_TITLE)
    val title = if (rawTitle.isNullOrEmpty()) Fallbacks.UNTITLED_EVENT else rawTitle

    val color = if (isNull(InstanceProjection.IDX_EVENT_COLOR)) {
        getInt(InstanceProjection.IDX_CALENDAR_COLOR)
    } else {
        getInt(InstanceProjection.IDX_EVENT_COLOR)
    }

    return EventInstance(
        instanceId = getLong(InstanceProjection.IDX_INSTANCE_ID),
        eventId = getLong(InstanceProjection.IDX_EVENT_ID),
        calendarId = getLong(InstanceProjection.IDX_CALENDAR_ID),
        title = title,
        start = begin.toKotlinInstantFromEpochMillis(),
        end = end.toKotlinInstantFromEpochMillis(),
        isAllDay = getInt(InstanceProjection.IDX_ALL_DAY) != 0,
        color = color,
        location = getString(InstanceProjection.IDX_LOCATION),
    )
}
