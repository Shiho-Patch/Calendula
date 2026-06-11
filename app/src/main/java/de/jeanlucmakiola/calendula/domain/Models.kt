package de.jeanlucmakiola.calendula.domain

import kotlin.time.Instant

data class CalendarSource(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val isVisibleInSystem: Boolean,
    /**
     * Whether events in this calendar can be created/edited/deleted
     * (`Calendars.CALENDAR_ACCESS_LEVEL` >= contributor). False for WebCal
     * subscriptions, birthday calendars and other read-only sources.
     */
    val canModifyContents: Boolean = false,
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
)

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
)

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
