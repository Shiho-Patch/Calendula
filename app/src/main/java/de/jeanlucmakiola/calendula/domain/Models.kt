package de.jeanlucmakiola.calendula.domain

import kotlin.time.Instant

data class CalendarSource(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val isVisibleInSystem: Boolean,
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
)

data class Attendee(
    val name: String,
    val email: String?,
    val status: AttendeeStatus,
)

enum class AttendeeStatus {
    Accepted,
    Declined,
    Tentative,
    NeedsAction,
    Unknown,
}

enum class FailureReason {
    PermissionRevoked,
    NoCalendarsConfigured,
    ProviderUnavailable,
    EventNotFound,
    Unknown,
}
