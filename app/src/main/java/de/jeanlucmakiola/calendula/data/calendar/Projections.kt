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
        CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        // CalendarContract has no description column; for the local calendars we
        // own we stash one in CAL_SYNC1 (synced rows put their sync token here,
        // so the mapper only reads it for local calendars).
        DESCRIPTION_COLUMN,
        // The special-dates marker (CAL_SYNC2) — the durable identity the app
        // uses to recognise its own managed calendars, independent of any
        // stored preference id (which a backup restore / data wipe can lose).
        MANAGED_MARKER_COLUMN,
        CalendarContract.Calendars.SYNC_EVENTS,
    )

    const val DESCRIPTION_COLUMN: String = CalendarContract.Calendars.CAL_SYNC1
    const val MANAGED_MARKER_COLUMN: String = CalendarContract.Calendars.CAL_SYNC2

    /** Namespace prefix of every managed-calendar [MANAGED_MARKER_COLUMN] value. */
    const val MANAGED_MARKER_PREFIX = "calendula.specialdates"

    const val IDX_ID = 0
    const val IDX_DISPLAY_NAME = 1
    const val IDX_ACCOUNT_NAME = 2
    const val IDX_ACCOUNT_TYPE = 3
    const val IDX_COLOR = 4
    const val IDX_VISIBLE = 5
    const val IDX_ACCESS_LEVEL = 6
    const val IDX_DESCRIPTION = 7
    const val IDX_MANAGED_MARKER = 8
    const val IDX_SYNC_EVENTS = 9
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
        CalendarContract.Events.STATUS,
        CalendarContract.Events.AVAILABILITY,
        CalendarContract.Events.ACCESS_LEVEL,
        CalendarContract.Events.EVENT_TIMEZONE,
        CalendarContract.Events.SELF_ATTENDEE_STATUS,
        CalendarContract.Events.EVENT_COLOR_KEY,
        // Recurring rows carry DURATION instead of DTEND; the detail screen
        // needs it to render a series opened without a named occurrence.
        CalendarContract.Events.DURATION,
        // Non-null on a modified-occurrence exception row; "no RRULE" alone
        // can't tell an exception from a master (#68).
        CalendarContract.Events.ORIGINAL_ID,
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
    const val IDX_STATUS = 12
    const val IDX_AVAILABILITY = 13
    const val IDX_ACCESS_LEVEL = 14
    const val IDX_EVENT_TIMEZONE = 15
    const val IDX_SELF_ATTENDEE_STATUS = 16
    const val IDX_EVENT_COLOR_KEY = 17
    const val IDX_DURATION = 18
    const val IDX_ORIGINAL_ID = 19
}

/**
 * Master/one-off Events rows for a whole-calendar backup. Unlike
 * [EventDetailProjection] this reads `UID_2445` (to keep a row's identity across
 * backups) and `DURATION` (recurring rows carry it instead of DTEND). Modified-
 * occurrence and cancelled-exception rows are filtered out by the query
 * (`ORIGINAL_ID IS NULL`), so RECURRENCE-ID overrides and EXDATEs aren't
 * exported yet — a documented v1 limit (import skips them too).
 */
internal object EventExportProjection {
    val COLUMNS: Array<String> = arrayOf(
        CalendarContract.Events._ID,
        CalendarContract.Events.UID_2445,
        CalendarContract.Events.TITLE,
        CalendarContract.Events.DTSTART,
        CalendarContract.Events.DTEND,
        CalendarContract.Events.DURATION,
        CalendarContract.Events.ALL_DAY,
        CalendarContract.Events.EVENT_TIMEZONE,
        CalendarContract.Events.RRULE,
        CalendarContract.Events.EVENT_LOCATION,
        CalendarContract.Events.DESCRIPTION,
        CalendarContract.Events.STATUS,
        CalendarContract.Events.AVAILABILITY,
        CalendarContract.Events.CALENDAR_ID,
    )

    const val IDX_ID = 0
    const val IDX_UID = 1
    const val IDX_TITLE = 2
    const val IDX_DTSTART = 3
    const val IDX_DTEND = 4
    const val IDX_DURATION = 5
    const val IDX_ALL_DAY = 6
    const val IDX_EVENT_TIMEZONE = 7
    const val IDX_RRULE = 8
    const val IDX_LOCATION = 9
    const val IDX_DESCRIPTION = 10
    const val IDX_STATUS = 11
    const val IDX_AVAILABILITY = 12
    const val IDX_CALENDAR_ID = 13
}

/**
 * Master/one-off Events rows matched by a full-text search. Like
 * [EventExportProjection] it reads the Events table directly (so the search is
 * unbounded in time), carrying DURATION for recurring rows that have no DTEND.
 * Colour folds the calendar fallback like [InstanceProjection].
 */
internal object SearchProjection {
    val COLUMNS: Array<String> = arrayOf(
        CalendarContract.Events._ID,
        CalendarContract.Events.CALENDAR_ID,
        CalendarContract.Events.TITLE,
        CalendarContract.Events.DTSTART,
        CalendarContract.Events.DTEND,
        CalendarContract.Events.DURATION,
        CalendarContract.Events.ALL_DAY,
        CalendarContract.Events.EVENT_COLOR,
        CalendarContract.Events.CALENDAR_COLOR,
        CalendarContract.Events.EVENT_LOCATION,
        // Recurrence markers: a non-empty RRULE or RDATE means the result should
        // display its nearest occurrence, not the series-start DTSTART.
        CalendarContract.Events.RRULE,
        CalendarContract.Events.RDATE,
    )

    const val IDX_ID = 0
    const val IDX_CALENDAR_ID = 1
    const val IDX_TITLE = 2
    const val IDX_DTSTART = 3
    const val IDX_DTEND = 4
    const val IDX_DURATION = 5
    const val IDX_ALL_DAY = 6
    const val IDX_EVENT_COLOR = 7
    const val IDX_CALENDAR_COLOR = 8
    const val IDX_LOCATION = 9
    const val IDX_RRULE = 10
    const val IDX_RDATE = 11
}

/**
 * The master/one-off Events row of an event about to be moved to another
 * calendar, read for a verbatim re-insert (see [MasterEventSnapshot]). Carries
 * the full recurrence skeleton (RRULE/DURATION, RDATE/EXDATE) so the moved copy
 * generates the same instances, and `UID_2445` so identity survives the move.
 */
internal object MoveMasterProjection {
    val COLUMNS: Array<String> = arrayOf(
        CalendarContract.Events.UID_2445,
        CalendarContract.Events.TITLE,
        CalendarContract.Events.DTSTART,
        CalendarContract.Events.DTEND,
        CalendarContract.Events.DURATION,
        CalendarContract.Events.RRULE,
        CalendarContract.Events.RDATE,
        CalendarContract.Events.EXDATE,
        CalendarContract.Events.EVENT_TIMEZONE,
        CalendarContract.Events.ALL_DAY,
        CalendarContract.Events.AVAILABILITY,
        CalendarContract.Events.ACCESS_LEVEL,
        CalendarContract.Events.STATUS,
        CalendarContract.Events.EVENT_LOCATION,
        CalendarContract.Events.DESCRIPTION,
    )

    const val IDX_UID = 0
    const val IDX_TITLE = 1
    const val IDX_DTSTART = 2
    const val IDX_DTEND = 3
    const val IDX_DURATION = 4
    const val IDX_RRULE = 5
    const val IDX_RDATE = 6
    const val IDX_EXDATE = 7
    const val IDX_EVENT_TIMEZONE = 8
    const val IDX_ALL_DAY = 9
    const val IDX_AVAILABILITY = 10
    const val IDX_ACCESS_LEVEL = 11
    const val IDX_STATUS = 12
    const val IDX_LOCATION = 13
    const val IDX_DESCRIPTION = 14
}

/**
 * The exception rows of a recurring series (`ORIGINAL_ID` = the series), read to
 * replay them against a moved copy (see [ExceptionRowSnapshot]). Both modified
 * occurrences and cancellations (`STATUS_CANCELED`) are read; the query filters
 * `DELETED = 0` so provider tombstones aren't replayed.
 */
internal object ExceptionProjection {
    val COLUMNS: Array<String> = arrayOf(
        CalendarContract.Events._ID,
        CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
        CalendarContract.Events.STATUS,
        CalendarContract.Events.TITLE,
        CalendarContract.Events.ALL_DAY,
        CalendarContract.Events.DTSTART,
        CalendarContract.Events.DTEND,
        CalendarContract.Events.DURATION,
        CalendarContract.Events.EVENT_TIMEZONE,
        CalendarContract.Events.AVAILABILITY,
        CalendarContract.Events.ACCESS_LEVEL,
        CalendarContract.Events.EVENT_LOCATION,
        CalendarContract.Events.DESCRIPTION,
    )

    const val IDX_ID = 0
    const val IDX_ORIGINAL_INSTANCE_TIME = 1
    const val IDX_STATUS = 2
    const val IDX_TITLE = 3
    const val IDX_ALL_DAY = 4
    const val IDX_DTSTART = 5
    const val IDX_DTEND = 6
    const val IDX_DURATION = 7
    const val IDX_EVENT_TIMEZONE = 8
    const val IDX_AVAILABILITY = 9
    const val IDX_ACCESS_LEVEL = 10
    const val IDX_LOCATION = 11
    const val IDX_DESCRIPTION = 12
}

internal object AttendeeProjection {
    val COLUMNS: Array<String> = arrayOf(
        CalendarContract.Attendees.ATTENDEE_NAME,
        CalendarContract.Attendees.ATTENDEE_EMAIL,
        CalendarContract.Attendees.ATTENDEE_STATUS,
        CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
        CalendarContract.Attendees.ATTENDEE_TYPE,
    )

    const val IDX_NAME = 0
    const val IDX_EMAIL = 1
    const val IDX_STATUS = 2
    const val IDX_RELATIONSHIP = 3
    const val IDX_TYPE = 4
}

internal object ReminderProjection {
    val COLUMNS: Array<String> = arrayOf(
        CalendarContract.Reminders.MINUTES,
        CalendarContract.Reminders.METHOD,
    )

    const val IDX_MINUTES = 0
    const val IDX_METHOD = 1
}

internal object Fallbacks {
    const val UNNAMED_CALENDAR = "(Unbenannter Kalender)"
    const val UNTITLED_EVENT = "(Ohne Titel)"
}
