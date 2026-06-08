package de.jeanlucmakiola.calendula.data.calendar

import android.provider.CalendarContract

internal object CalendarProjection {
    val COLUMNS: Array<String> = arrayOf(
        CalendarContract.Calendars._ID,
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        CalendarContract.Calendars.ACCOUNT_NAME,
        CalendarContract.Calendars.ACCOUNT_TYPE,
        CalendarContract.Calendars.CALENDAR_COLOR,
        CalendarContract.Calendars.VISIBLE,
    )

    const val IDX_ID = 0
    const val IDX_DISPLAY_NAME = 1
    const val IDX_ACCOUNT_NAME = 2
    const val IDX_ACCOUNT_TYPE = 3
    const val IDX_COLOR = 4
    const val IDX_VISIBLE = 5
}

internal object InstanceProjection {
    val COLUMNS: Array<String> = arrayOf(
        CalendarContract.Instances._ID,
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.CALENDAR_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.EVENT_COLOR,
        CalendarContract.Instances.CALENDAR_COLOR,
        CalendarContract.Instances.EVENT_LOCATION,
    )

    const val IDX_INSTANCE_ID = 0
    const val IDX_EVENT_ID = 1
    const val IDX_CALENDAR_ID = 2
    const val IDX_TITLE = 3
    const val IDX_BEGIN = 4
    const val IDX_END = 5
    const val IDX_ALL_DAY = 6
    const val IDX_EVENT_COLOR = 7
    const val IDX_CALENDAR_COLOR = 8
    const val IDX_LOCATION = 9
}

internal object EventDetailProjection {
    val COLUMNS: Array<String> = arrayOf(
        CalendarContract.Events._ID,
        CalendarContract.Events.TITLE,
        CalendarContract.Events.DESCRIPTION,
        CalendarContract.Events.ORGANIZER,
        CalendarContract.Events.RRULE,
        CalendarContract.Events.EVENT_COLOR,
        CalendarContract.Events.CALENDAR_COLOR,
        CalendarContract.Events.DTSTART,
        CalendarContract.Events.DTEND,
        CalendarContract.Events.ALL_DAY,
        CalendarContract.Events.EVENT_LOCATION,
        CalendarContract.Events.CALENDAR_ID,
    )

    const val IDX_EVENT_ID = 0
    const val IDX_TITLE = 1
    const val IDX_DESCRIPTION = 2
    const val IDX_ORGANIZER = 3
    const val IDX_RRULE = 4
    const val IDX_EVENT_COLOR = 5
    const val IDX_CALENDAR_COLOR = 6
    const val IDX_DTSTART = 7
    const val IDX_DTEND = 8
    const val IDX_ALL_DAY = 9
    const val IDX_LOCATION = 10
    const val IDX_CALENDAR_ID = 11
}

internal object AttendeeProjection {
    val COLUMNS: Array<String> = arrayOf(
        CalendarContract.Attendees.ATTENDEE_NAME,
        CalendarContract.Attendees.ATTENDEE_EMAIL,
        CalendarContract.Attendees.ATTENDEE_STATUS,
    )

    const val IDX_NAME = 0
    const val IDX_EMAIL = 1
    const val IDX_STATUS = 2
}

internal object Fallbacks {
    const val UNNAMED_CALENDAR = "(Unbenannter Kalender)"
    const val UNTITLED_EVENT = "(Ohne Titel)"
}
