package de.jeanlucmakiola.calendula.data.calendar

import android.provider.CalendarContract
import android.util.Log
import de.jeanlucmakiola.calendula.domain.AccessLevel
import de.jeanlucmakiola.calendula.domain.Attendee
import de.jeanlucmakiola.calendula.domain.AttendeeRelationship
import de.jeanlucmakiola.calendula.domain.AttendeeStatus
import de.jeanlucmakiola.calendula.domain.AttendeeType
import de.jeanlucmakiola.calendula.domain.Availability
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.EventStatus
import de.jeanlucmakiola.calendula.domain.Reminder
import de.jeanlucmakiola.calendula.domain.ReminderMethod

private const val TAG = "EventDetailMapper"

internal fun ColumnReader.toEventDetailCore(
    attendees: List<Attendee>,
    reminders: List<Reminder>,
): EventDetail? {
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

    // STATUS shares its 0 value with STATUS_TENTATIVE, so a missing column must
    // be distinguished from a present 0 — an absent status means "just confirmed".
    val status = if (isNull(EventDetailProjection.IDX_STATUS)) {
        EventStatus.Confirmed
    } else {
        mapEventStatus(getInt(EventDetailProjection.IDX_STATUS))
    }

    return EventDetail(
        instance = instance,
        description = getString(EventDetailProjection.IDX_DESCRIPTION),
        organizer = getString(EventDetailProjection.IDX_ORGANIZER),
        attendees = attendees,
        rrule = getString(EventDetailProjection.IDX_RRULE),
        reminders = reminders,
        status = status,
        // BUSY and DEFAULT are both 0, so a null column folds into the same
        // default these mappers already return — no isNull guard needed.
        availability = mapAvailability(getInt(EventDetailProjection.IDX_AVAILABILITY)),
        accessLevel = mapAccessLevel(getInt(EventDetailProjection.IDX_ACCESS_LEVEL)),
        eventTimezone = getString(EventDetailProjection.IDX_EVENT_TIMEZONE),
        selfStatus = mapAttendeeStatus(getInt(EventDetailProjection.IDX_SELF_ATTENDEE_STATUS)),
    )
}

internal fun ColumnReader.toAttendee(): Attendee = Attendee(
    name = getString(AttendeeProjection.IDX_NAME).orEmpty(),
    email = getString(AttendeeProjection.IDX_EMAIL),
    status = mapAttendeeStatus(getInt(AttendeeProjection.IDX_STATUS)),
    relationship = mapAttendeeRelationship(getInt(AttendeeProjection.IDX_RELATIONSHIP)),
    type = mapAttendeeType(getInt(AttendeeProjection.IDX_TYPE)),
)

internal fun ColumnReader.toReminder(): Reminder = Reminder(
    minutes = getInt(ReminderProjection.IDX_MINUTES),
    method = mapReminderMethod(getInt(ReminderProjection.IDX_METHOD)),
)

internal fun mapAttendeeStatus(raw: Int): AttendeeStatus = when (raw) {
    CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED -> AttendeeStatus.Accepted
    CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED -> AttendeeStatus.Declined
    CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE -> AttendeeStatus.Tentative
    CalendarContract.Attendees.ATTENDEE_STATUS_INVITED -> AttendeeStatus.NeedsAction
    else -> AttendeeStatus.Unknown
}

internal fun mapAttendeeRelationship(raw: Int): AttendeeRelationship = when (raw) {
    CalendarContract.Attendees.RELATIONSHIP_ORGANIZER -> AttendeeRelationship.Organizer
    CalendarContract.Attendees.RELATIONSHIP_PERFORMER -> AttendeeRelationship.Performer
    CalendarContract.Attendees.RELATIONSHIP_SPEAKER -> AttendeeRelationship.Speaker
    CalendarContract.Attendees.RELATIONSHIP_ATTENDEE -> AttendeeRelationship.Attendee
    else -> AttendeeRelationship.None
}

internal fun mapAttendeeType(raw: Int): AttendeeType = when (raw) {
    CalendarContract.Attendees.TYPE_REQUIRED -> AttendeeType.Required
    CalendarContract.Attendees.TYPE_OPTIONAL -> AttendeeType.Optional
    CalendarContract.Attendees.TYPE_RESOURCE -> AttendeeType.Resource
    else -> AttendeeType.None
}

internal fun mapEventStatus(raw: Int): EventStatus = when (raw) {
    CalendarContract.Events.STATUS_CONFIRMED -> EventStatus.Confirmed
    CalendarContract.Events.STATUS_TENTATIVE -> EventStatus.Tentative
    CalendarContract.Events.STATUS_CANCELED -> EventStatus.Cancelled
    else -> EventStatus.Confirmed
}

internal fun mapAvailability(raw: Int): Availability = when (raw) {
    CalendarContract.Events.AVAILABILITY_FREE -> Availability.Free
    CalendarContract.Events.AVAILABILITY_TENTATIVE -> Availability.Tentative
    else -> Availability.Busy
}

internal fun mapAccessLevel(raw: Int): AccessLevel = when (raw) {
    CalendarContract.Events.ACCESS_CONFIDENTIAL -> AccessLevel.Confidential
    CalendarContract.Events.ACCESS_PRIVATE -> AccessLevel.Private
    CalendarContract.Events.ACCESS_PUBLIC -> AccessLevel.Public
    else -> AccessLevel.Default
}

internal fun mapReminderMethod(raw: Int): ReminderMethod = when (raw) {
    CalendarContract.Reminders.METHOD_EMAIL -> ReminderMethod.Email
    CalendarContract.Reminders.METHOD_SMS -> ReminderMethod.Sms
    CalendarContract.Reminders.METHOD_ALARM -> ReminderMethod.Alarm
    CalendarContract.Reminders.METHOD_ALERT -> ReminderMethod.Alert
    else -> ReminderMethod.Default
}
