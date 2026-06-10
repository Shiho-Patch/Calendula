package de.jeanlucmakiola.calendula.data.calendar

import android.provider.CalendarContract
import android.util.Log
import de.jeanlucmakiola.calendula.domain.Attendee
import de.jeanlucmakiola.calendula.domain.AttendeeStatus
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventInstance

private const val TAG = "EventDetailMapper"

internal fun ColumnReader.toEventDetailCore(attendees: List<Attendee>): EventDetail? {
    val begin = getLong(EventDetailProjection.IDX_DTSTART)

    if (begin < 0L) {
        Log.w(TAG, "Dropping event with negative dtstart=$begin")
        return null
    }

    // Recurring events store DURATION instead of DTEND, so the series row's
    // DTEND is null. Keep the event (end == begin); callers that opened a
    // specific occurrence supply the real per-occurrence times from
    // CalendarContract.Instances. Only a present-but-backwards DTEND is malformed.
    val end = if (isNull(EventDetailProjection.IDX_DTEND)) {
        begin
    } else {
        val rawEnd = getLong(EventDetailProjection.IDX_DTEND)
        if (rawEnd < begin) {
            Log.w(TAG, "Dropping event with dtend=$rawEnd < dtstart=$begin")
            return null
        }
        rawEnd
    }

    val rawTitle = getString(EventDetailProjection.IDX_TITLE)
    val title = if (rawTitle.isNullOrEmpty()) Fallbacks.UNTITLED_EVENT else rawTitle

    val color = if (isNull(EventDetailProjection.IDX_EVENT_COLOR)) {
        getInt(EventDetailProjection.IDX_CALENDAR_COLOR)
    } else {
        getInt(EventDetailProjection.IDX_EVENT_COLOR)
    }

    val eventId = getLong(EventDetailProjection.IDX_EVENT_ID)
    val instance = EventInstance(
        instanceId = eventId,
        eventId = eventId,
        calendarId = getLong(EventDetailProjection.IDX_CALENDAR_ID),
        title = title,
        start = begin.toKotlinInstantFromEpochMillis(),
        end = end.toKotlinInstantFromEpochMillis(),
        isAllDay = getInt(EventDetailProjection.IDX_ALL_DAY) != 0,
        color = color,
        location = getString(EventDetailProjection.IDX_LOCATION),
    )

    return EventDetail(
        instance = instance,
        description = getString(EventDetailProjection.IDX_DESCRIPTION),
        organizer = getString(EventDetailProjection.IDX_ORGANIZER),
        attendees = attendees,
        rrule = getString(EventDetailProjection.IDX_RRULE),
    )
}

internal fun ColumnReader.toAttendee(): Attendee = Attendee(
    name = getString(AttendeeProjection.IDX_NAME).orEmpty(),
    email = getString(AttendeeProjection.IDX_EMAIL),
    status = mapAttendeeStatus(getInt(AttendeeProjection.IDX_STATUS)),
)

internal fun mapAttendeeStatus(raw: Int): AttendeeStatus = when (raw) {
    CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED -> AttendeeStatus.Accepted
    CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED -> AttendeeStatus.Declined
    CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE -> AttendeeStatus.Tentative
    CalendarContract.Attendees.ATTENDEE_STATUS_INVITED -> AttendeeStatus.NeedsAction
    else -> AttendeeStatus.Unknown
}
