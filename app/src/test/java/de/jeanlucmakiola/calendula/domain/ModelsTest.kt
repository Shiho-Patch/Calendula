package de.jeanlucmakiola.calendula.domain

import com.google.common.truth.Truth.assertThat
import kotlin.time.Instant
import org.junit.jupiter.api.Test

class ModelsTest {

    @Test
    fun `CalendarSource is a data class with structural equality`() {
        val a = CalendarSource(1L, "Work", "x@y", "com.google", 0xFF112233.toInt(), true)
        val b = CalendarSource(1L, "Work", "x@y", "com.google", 0xFF112233.toInt(), true)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `EventInstance is a data class with structural equality`() {
        val start = Instant.fromEpochMilliseconds(0L)
        val end = Instant.fromEpochMilliseconds(3_600_000L)
        val a = EventInstance(10L, 1L, 1L, "Meet", start, end, false, 0xFF000000.toInt(), null)
        val b = EventInstance(10L, 1L, 1L, "Meet", start, end, false, 0xFF000000.toInt(), null)
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `AttendeeStatus enum has all five variants`() {
        assertThat(AttendeeStatus.values().toSet()).isEqualTo(
            setOf(
                AttendeeStatus.Accepted,
                AttendeeStatus.Declined,
                AttendeeStatus.Tentative,
                AttendeeStatus.NeedsAction,
                AttendeeStatus.Unknown,
            )
        )
    }

    @Test
    fun `FailureReason enum has all six variants`() {
        assertThat(FailureReason.values().toSet()).isEqualTo(
            setOf(
                FailureReason.PermissionRevoked,
                FailureReason.NoCalendarsConfigured,
                FailureReason.AllCalendarsHidden,
                FailureReason.ProviderUnavailable,
                FailureReason.EventNotFound,
                FailureReason.Unknown,
            )
        )
    }

    @Test
    fun `EventDetail composes EventInstance plus extras`() {
        val instance = EventInstance(
            instanceId = 10L,
            eventId = 1L,
            calendarId = 1L,
            title = "Meet",
            start = Instant.fromEpochMilliseconds(0L),
            end = Instant.fromEpochMilliseconds(60_000L),
            isAllDay = false,
            color = 0xFFAABBCC.toInt(),
            location = null,
        )
        val detail = EventDetail(
            instance = instance,
            description = "Brief description",
            organizer = "x@y",
            attendees = listOf(Attendee("Alice", "alice@x", AttendeeStatus.Accepted)),
            rrule = "FREQ=WEEKLY",
        )
        assertThat(detail.instance.title).isEqualTo("Meet")
        assertThat(detail.attendees).hasSize(1)
    }
}
