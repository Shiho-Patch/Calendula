package de.jeanlucmakiola.calendula.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

data class CalendarSource(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    /**
     * The system's `Calendars.VISIBLE` flag — the single visibility model,
     * deciding both what Calendula shows and whether this calendar plans
     * reminders (#75). The drawer's filter sheet is a separate in-app declutter.
     */
    val isVisibleInSystem: Boolean,
    /**
     * Whether events in this calendar can be created/edited/deleted
     * (`Calendars.CALENDAR_ACCESS_LEVEL` >= contributor). False for WebCal
     * subscriptions, birthday calendars and other read-only sources.
     */
    val canModifyContents: Boolean = false,
    /**
     * A device-only calendar the app itself owns (`ACCOUNT_TYPE_LOCAL`): it has
     * no sync backend, so the app can rename / recolor / delete it. Synced
     * calendars (Google, DAVx5, …) are managed in their own source app instead.
     */
    val isLocal: Boolean = false,
    /**
     * Free-text note for a local calendar (stored in `CAL_SYNC1`, which the app
     * owns for its own calendars). Always null for synced calendars.
     */
    val description: String? = null,
    /**
     * A special-dates mirror calendar the app manages (birthdays/anniversaries
     * from contacts). Its events' title/date/recurrence are owned by the sync,
     * so it's hidden from the new-event calendar picker and its events lock those
     * fields in the editor. Recognised by a durable provider marker, so it holds
     * even after a backup restore clears the app's stored ids.
     */
    val isManaged: Boolean = false,
    /**
     * Whether the provider keeps this calendar's events on the device
     * (`Calendars.SYNC_EVENTS`), independent of [isVisibleInSystem]. Says
     * nothing about device-local calendars, which can hold events with it off.
     * Read for the "not synced" row label (#76).
     */
    val syncsEvents: Boolean = true,
)

data class EventInstance(
    val instanceId: Long,
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val start: Instant,
    val end: Instant,
    val isAllDay: Boolean,
    val color: Int,
    val location: String?,
    /**
     * Only search results, which read the series master, fill this in — the
     * Instances query already yields one row per occurrence.
     */
    val isRecurring: Boolean = false,
    /**
     * This device user answered "no" to the invitation
     * (`Events.SELF_ATTENDEE_STATUS`). The event stays on the calendar — it is
     * still an appointment someone expects an answer about — but every surface
     * strikes it through, and it plans no reminders (#180).
     */
    val isDeclined: Boolean = false,
)

/**
 * Whether this event has finished relative to [now] — its end is at or before
 * the current instant. An in-progress event (already started but not yet ended)
 * is *not* considered ended. All-day events end at the exclusive next-midnight,
 * so they only count as ended once their day is fully over.
 */
fun EventInstance.hasEnded(now: Instant): Boolean = end <= now

/**
 * The zone this event's calendar dates live in: the device [zone] for timed
 * events, UTC for all-day ones, whose midnights would otherwise shift day
 * boundaries (#65, #82). Every surface naming an all-day date goes through here.
 */
fun EventInstance.dateZone(zone: TimeZone): TimeZone =
    if (isAllDay) TimeZone.UTC else zone

/** The first calendar day this event occupies. */
fun EventInstance.spanFirstDay(zone: TimeZone): LocalDate =
    start.toLocalDateTime(dateZone(zone)).date

/**
 * The last calendar day this event occupies. An event ending exactly at midnight
 * does not reach into that day, so resolve just before [EventInstance.end].
 */
fun EventInstance.spanLastDay(zone: TimeZone): LocalDate {
    val lastInstant = if (end > start) end - 1.milliseconds else start
    return lastInstant.toLocalDateTime(dateZone(zone)).date
}

/** Whether this event occupies more than one calendar day in [zone]. */
fun EventInstance.spansMultipleDays(zone: TimeZone): Boolean =
    spanFirstDay(zone) != spanLastDay(zone)

data class EventDetail(
    val instance: EventInstance,
    val description: String?,
    val organizer: String?,
    val attendees: List<Attendee>,
    val rrule: String?,
    /** Reminders (VALARM) configured on the event, ascending lead time. */
    val reminders: List<Reminder> = emptyList(),
    /** Confirmed / Tentative / Cancelled (`Events.STATUS`). */
    val status: EventStatus = EventStatus.Confirmed,
    /** Busy / Free (`Events.AVAILABILITY`, the iCal TRANSP field). */
    val availability: Availability = Availability.Busy,
    /** Default / Private / Confidential / Public (`Events.ACCESS_LEVEL`). */
    val accessLevel: AccessLevel = AccessLevel.Default,
    /** Raw zone id (`Events.EVENT_TIMEZONE`), e.g. "Europe/Berlin"; null if unset. */
    val eventTimezone: String? = null,
    /** This device user's own response (`Events.SELF_ATTENDEE_STATUS`). */
    val selfStatus: AttendeeStatus = AttendeeStatus.Unknown,
    /**
     * The event's own raw colour (`Events.EVENT_COLOR`), null when the event
     * inherits its calendar's colour. Unlike [EventInstance.color] (which
     * already folds in the calendar fallback for display) this stays null so
     * the edit form can tell "has own colour" from "inherits".
     */
    val eventColor: Int? = null,
    /** The event's `Events.EVENT_COLOR_KEY` (a calendar-palette key), or null. */
    val eventColorKey: String? = null,
    /**
     * True when this row is a modified occurrence of a series (`ORIGINAL_ID` is
     * set) rather than a master, so a reschedule takes the plain whole-row path
     * whatever [rrule] a sync adapter left on it.
     */
    val isException: Boolean = false,
)

/**
 * One selectable event colour published by a calendar's account
 * (`CalendarContract.Colors`, `TYPE_EVENT`): [key] is the account-scoped
 * `COLOR_KEY` written as `EVENT_COLOR_KEY` (so the colour survives sync),
 * [argb] is the swatch it renders as.
 */
data class EventColorOption(val key: String, val argb: Int)

data class Attendee(
    val name: String,
    val email: String?,
    val status: AttendeeStatus,
    /** Organizer / performer / speaker / plain attendee (`ATTENDEE_RELATIONSHIP`). */
    val relationship: AttendeeRelationship = AttendeeRelationship.None,
    /** Required / optional / resource (`ATTENDEE_TYPE`). */
    val type: AttendeeType = AttendeeType.None,
)

data class Reminder(
    /** Lead time before the event start, in minutes. `-1` means the provider default. */
    val minutes: Int,
    val method: ReminderMethod,
)

enum class AttendeeStatus {
    Accepted,
    Declined,
    Tentative,
    NeedsAction,
    Unknown,
}

enum class AttendeeRelationship {
    Organizer,
    Attendee,
    Performer,
    Speaker,
    None,
}

enum class AttendeeType {
    Required,
    Optional,
    Resource,
    None,
}

enum class ReminderMethod {
    Alert,
    Email,
    Sms,
    Alarm,
    Default,
}

enum class EventStatus {
    Confirmed,
    Tentative,
    Cancelled,
}

enum class Availability {
    Busy,
    Free,
    Tentative,
}

enum class AccessLevel {
    Default,
    Public,
    Private,
    Confidential,
}

/**
 * How far a write to a recurring event reaches. Non-recurring events always
 * use [AllEvents] (there is only one).
 */
enum class RecurringWriteScope {
    ThisEvent,
    ThisAndFollowing,
    AllEvents,
}

enum class FailureReason {
    PermissionRevoked,
    NoCalendarsConfigured,
    ProviderUnavailable,
    EventNotFound,
    Unknown,
}
