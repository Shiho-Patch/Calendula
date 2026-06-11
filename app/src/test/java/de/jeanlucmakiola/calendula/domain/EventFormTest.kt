package de.jeanlucmakiola.calendula.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class EventFormTest {

    private fun form(
        calendarId: Long? = 1L,
        isAllDay: Boolean = false,
        start: LocalDateTime = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(10, 0)),
        end: LocalDateTime = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(11, 0)),
    ): EventForm = EventForm(calendarId = calendarId, isAllDay = isAllDay, start = start, end = end)

    @Test
    fun `valid timed form has no problems`() {
        assertThat(form().problems()).isEmpty()
    }

    @Test
    fun `missing calendar is a problem`() {
        assertThat(form(calendarId = null).problems())
            .containsExactly(EventFormProblem.NoCalendar)
    }

    @Test
    fun `timed end before start is a problem`() {
        val bad = form(end = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(9, 0)))
        assertThat(bad.problems()).containsExactly(EventFormProblem.EndBeforeStart)
    }

    @Test
    fun `zero-length timed event is allowed`() {
        val instant = form(end = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(10, 0)))
        assertThat(instant.problems()).isEmpty()
    }

    @Test
    fun `all-day single day is allowed even though times match`() {
        val allDay = form(
            isAllDay = true,
            start = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(0, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(0, 0)),
        )
        assertThat(allDay.problems()).isEmpty()
    }

    @Test
    fun `all-day end date before start date is a problem`() {
        val bad = form(
            isAllDay = true,
            start = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(0, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 10), LocalTime(0, 0)),
        )
        assertThat(bad.problems()).containsExactly(EventFormProblem.EndBeforeStart)
    }

    @Test
    fun `problems accumulate`() {
        val bad = form(
            calendarId = null,
            end = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(9, 0)),
        )
        assertThat(bad.problems()).containsExactly(
            EventFormProblem.NoCalendar,
            EventFormProblem.EndBeforeStart,
        )
    }

    @Test
    fun `recurrence until before the first day is a problem`() {
        // Days before the start, so it parses to an earlier date in any zone.
        val bad = form().copy(rrule = "FREQ=DAILY;UNTIL=20260601T120000Z")
        assertThat(bad.problems())
            .containsExactly(EventFormProblem.RecurrenceEndsBeforeStart)
    }

    @Test
    fun `recurrence until on or after the first day is fine`() {
        // Date-only UNTIL parses zone-independently.
        assertThat(form().copy(rrule = "FREQ=DAILY;UNTIL=20260611").problems()).isEmpty()
        assertThat(form().copy(rrule = "FREQ=WEEKLY").problems()).isEmpty()
    }

    @Test
    fun `complex rrules are not validated against the start`() {
        // The picker can't have produced this ("second Monday" ordinal BYDAY);
        // it is preserved verbatim and never flagged.
        assertThat(form().copy(rrule = "FREQ=MONTHLY;BYDAY=2MO;UNTIL=20200101").problems()).isEmpty()
    }

    @Test
    fun `weekly byday rules are validated against the start`() {
        val bad = form().copy(rrule = "FREQ=WEEKLY;BYDAY=MO,FR;UNTIL=20200101")
        assertThat(bad.problems())
            .containsExactly(EventFormProblem.RecurrenceEndsBeforeStart)
    }

    private val berlin = TimeZone.of("Europe/Berlin")

    private fun detail(
        isAllDay: Boolean = false,
        title: String = "Stand-up",
        location: String? = "Berlin",
        description: String? = "Body",
        rrule: String? = null,
        reminders: List<Reminder> = emptyList(),
        availability: Availability = Availability.Busy,
        accessLevel: AccessLevel = AccessLevel.Default,
    ): EventDetail = EventDetail(
        instance = EventInstance(
            instanceId = 1L,
            eventId = 1L,
            calendarId = 7L,
            title = title,
            start = Instant.fromEpochMilliseconds(0L),
            end = Instant.fromEpochMilliseconds(0L),
            isAllDay = isAllDay,
            color = 0,
            location = location,
        ),
        description = description,
        organizer = null,
        attendees = emptyList(),
        rrule = rrule,
        reminders = reminders,
        availability = availability,
        accessLevel = accessLevel,
    )

    @Test
    fun `toEditForm prefills a timed event from the occurrence times`() {
        // 2026-06-11 is CEST (UTC+2): 08:00Z == 10:00 local.
        val prefilled = detail().toEditForm(
            beginMillis = 1_781_164_800_000L,
            endMillis = 1_781_164_800_000L + 90L * 60L * 1000L,
            zone = berlin,
        )
        assertThat(prefilled.start).isEqualTo(LocalDateTime(LocalDate(2026, 6, 11), LocalTime(10, 0)))
        assertThat(prefilled.end).isEqualTo(LocalDateTime(LocalDate(2026, 6, 11), LocalTime(11, 30)))
        assertThat(prefilled.isAllDay).isFalse()
        assertThat(prefilled.calendarId).isEqualTo(7L)
        assertThat(prefilled.title).isEqualTo("Stand-up")
        assertThat(prefilled.location).isEqualTo("Berlin")
        assertThat(prefilled.description).isEqualTo("Body")
    }

    @Test
    fun `toEditForm turns the exclusive all-day end into the last covered day`() {
        // 11th..13th = UTC midnights of the 11th and the (exclusive) 14th.
        val prefilled = detail(isAllDay = true).toEditForm(
            beginMillis = LocalDate(2026, 6, 11).toEpochDays() * 86_400_000L,
            endMillis = LocalDate(2026, 6, 14).toEpochDays() * 86_400_000L,
            zone = berlin,
        )
        assertThat(prefilled.start.date).isEqualTo(LocalDate(2026, 6, 11))
        assertThat(prefilled.end.date).isEqualTo(LocalDate(2026, 6, 13))
        assertThat(prefilled.isAllDay).isTrue()
    }

    @Test
    fun `toEditForm sorts and dedupes reminder minutes and strips an RRULE prefix`() {
        val prefilled = detail(
            rrule = "RRULE:FREQ=WEEKLY",
            reminders = listOf(
                Reminder(30, ReminderMethod.Email),
                Reminder(10, ReminderMethod.Alert),
                Reminder(30, ReminderMethod.Alert),
            ),
        ).toEditForm(beginMillis = 0L, endMillis = 3_600_000L, zone = berlin)
        assertThat(prefilled.reminders).containsExactly(10, 30).inOrder()
        assertThat(prefilled.rrule).isEqualTo("FREQ=WEEKLY")
    }

    @Test
    fun `populatedFields reports exactly the sections holding values`() {
        val empty = form().copy(location = "", description = "")
        assertThat(empty.populatedFields()).isEmpty()

        val full = form().copy(
            location = "Berlin",
            description = "Body",
            reminders = listOf(10),
            rrule = "FREQ=DAILY",
            availability = Availability.Free,
            accessLevel = AccessLevel.Private,
        )
        assertThat(full.populatedFields()).containsExactly(
            EventFormField.Location,
            EventFormField.Description,
            EventFormField.Reminders,
            EventFormField.Recurrence,
            EventFormField.Availability,
            EventFormField.Visibility,
        )
    }
}
