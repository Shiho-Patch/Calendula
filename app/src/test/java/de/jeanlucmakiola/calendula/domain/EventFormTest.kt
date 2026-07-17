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
        rowStart: Long = 0L,
        rowEnd: Long = 0L,
        attendees: List<Attendee> = emptyList(),
        eventColor: Int? = null,
        eventColorKey: String? = null,
        eventTimezone: String? = null,
    ): EventDetail = EventDetail(
        instance = EventInstance(
            instanceId = 1L,
            eventId = 1L,
            calendarId = 7L,
            title = title,
            start = Instant.fromEpochMilliseconds(rowStart),
            end = Instant.fromEpochMilliseconds(rowEnd),
            isAllDay = isAllDay,
            color = 0,
            location = location,
        ),
        description = description,
        organizer = null,
        attendees = attendees,
        rrule = rrule,
        reminders = reminders,
        availability = availability,
        accessLevel = accessLevel,
        eventColor = eventColor,
        eventColorKey = eventColorKey,
        eventTimezone = eventTimezone,
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
    fun `toEditForm leaves an event in the device zone unpinned`() {
        val prefilled = detail(eventTimezone = "Europe/Berlin").toEditForm(
            beginMillis = 1_781_164_800_000L,
            endMillis = 1_781_164_800_000L + 3_600_000L,
            zone = berlin,
        )
        // Same zone as the device: pinning it would only make the picker appear
        // on every ordinary event.
        assertThat(prefilled.timezone).isNull()
        assertThat(prefilled.populatedFields()).doesNotContain(EventFormField.Timezone)
    }

    @Test
    fun `toEditForm pins a foreign zone and shows the times in it`() {
        val prefilled = detail(eventTimezone = "America/New_York").toEditForm(
            beginMillis = 1_781_164_800_000L, // 08:00Z
            endMillis = 1_781_164_800_000L + 3_600_000L,
            zone = berlin,
        )
        assertThat(prefilled.timezone).isEqualTo("America/New_York")
        // 08:00Z is 10:00 in Berlin but 04:00 in New York — the form shows the
        // event's own wall-clock, which is what a later save re-anchors to.
        assertThat(prefilled.start).isEqualTo(LocalDateTime(LocalDate(2026, 6, 11), LocalTime(4, 0)))
        assertThat(prefilled.populatedFields()).contains(EventFormField.Timezone)
    }

    @Test
    fun `toEditForm never pins a zone on an all-day event`() {
        // All-day rows carry a nominal "UTC" that is an anchor, not a location.
        val prefilled = detail(isAllDay = true, eventTimezone = "UTC").toEditForm(
            beginMillis = LocalDate(2026, 6, 11).toEpochDays() * 86_400_000L,
            endMillis = LocalDate(2026, 6, 12).toEpochDays() * 86_400_000L,
            zone = berlin,
        )
        assertThat(prefilled.timezone).isNull()
    }

    @Test
    fun `toEditForm ignores an unparseable stored zone`() {
        val prefilled = detail(eventTimezone = "Mars/Olympus_Mons").toEditForm(
            beginMillis = 1_781_164_800_000L,
            endMillis = 1_781_164_800_000L + 3_600_000L,
            zone = berlin,
        )
        // A malformed sync row must not be honoured, nor fail the open.
        assertThat(prefilled.timezone).isNull()
        assertThat(prefilled.start).isEqualTo(LocalDateTime(LocalDate(2026, 6, 11), LocalTime(10, 0)))
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
    fun `snapshots of an unchanged event are equal`() {
        val a = detail().toEditSnapshot(0L, 3_600_000L, berlin)
        val b = detail().toEditSnapshot(0L, 3_600_000L, berlin)
        assertThat(b).isEqualTo(a)
    }

    @Test
    fun `an external field change makes snapshots differ`() {
        val loaded = detail().toEditSnapshot(0L, 3_600_000L, berlin)
        val fresh = detail(title = "Stand-up (moved)").toEditSnapshot(0L, 3_600_000L, berlin)
        assertThat(fresh).isNotEqualTo(loaded)
    }

    @Test
    fun `an external time move is caught by the row times the form cannot see`() {
        // Both snapshots are taken for the same tapped occurrence, so the
        // *forms* derive identical times — only rowStart/rowEnd betray the move.
        val loaded = detail(rrule = "FREQ=WEEKLY", rowStart = 0L)
            .toEditSnapshot(0L, 3_600_000L, berlin)
        val fresh = detail(rrule = "FREQ=WEEKLY", rowStart = 86_400_000L)
            .toEditSnapshot(0L, 3_600_000L, berlin)
        assertThat(fresh.form).isEqualTo(loaded.form)
        assertThat(fresh).isNotEqualTo(loaded)
    }

    @Test
    fun `a reminder-method change the form cannot write does not fake a conflict`() {
        // The form carries reminder minutes only, not the method — so a method
        // flip the form can't express must not read as an external change.
        val loaded = detail(reminders = listOf(Reminder(10, ReminderMethod.Alert)))
            .toEditSnapshot(0L, 3_600_000L, berlin)
        val fresh = detail(reminders = listOf(Reminder(10, ReminderMethod.Email)))
            .toEditSnapshot(0L, 3_600_000L, berlin)
        assertThat(fresh).isEqualTo(loaded)
    }

    @Test
    fun `an external guest change now trips the conflict check`() {
        // Attendees are writable now, so they ride in the snapshot: adding a
        // guest externally is a real conflict, not invisible.
        val loaded = detail().toEditSnapshot(0L, 3_600_000L, berlin)
        val fresh = detail(
            attendees = listOf(Attendee("Ada", "ada@example.org", AttendeeStatus.NeedsAction)),
        ).toEditSnapshot(0L, 3_600_000L, berlin)
        assertThat(fresh).isNotEqualTo(loaded)
    }

    @Test
    fun `toEditForm carries editable guests with name email and optional role`() {
        val prefilled = detail(
            attendees = listOf(
                Attendee("Ada", "ada@example.org", AttendeeStatus.Accepted, type = AttendeeType.Required),
                Attendee("", "bob@example.org", AttendeeStatus.NeedsAction, type = AttendeeType.Optional),
            ),
        ).toEditForm(beginMillis = 0L, endMillis = 3_600_000L, zone = berlin)
        assertThat(prefilled.attendees).containsExactly(
            EventAttendee(email = "ada@example.org", name = "Ada", optional = false),
            EventAttendee(email = "bob@example.org", name = "", optional = true),
        ).inOrder()
        assertThat(prefilled.populatedFields()).contains(EventFormField.Attendees)
    }

    @Test
    fun `toEditForm drops the organizer, resources and guests without an email`() {
        val prefilled = detail(
            attendees = listOf(
                Attendee("Org", "org@example.org", AttendeeStatus.Accepted, AttendeeRelationship.Organizer),
                Attendee("Room A", "room@example.org", AttendeeStatus.Accepted, type = AttendeeType.Resource),
                Attendee("No address", null, AttendeeStatus.NeedsAction),
                Attendee("Ada", "ada@example.org", AttendeeStatus.NeedsAction),
            ),
        ).toEditForm(beginMillis = 0L, endMillis = 3_600_000L, zone = berlin)
        assertThat(prefilled.attendees).containsExactly(
            EventAttendee(email = "ada@example.org", name = "Ada", optional = false),
        )
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
            color = 0xFFD50000.toInt(),
        )
        assertThat(full.populatedFields()).containsExactly(
            EventFormField.Location,
            EventFormField.Description,
            EventFormField.Reminders,
            EventFormField.Recurrence,
            EventFormField.Availability,
            EventFormField.Visibility,
            EventFormField.Color,
        )
    }

    @Test
    fun `toEditForm carries a palette colour as key plus swatch`() {
        val prefilled = detail(eventColor = 0xFF33B679.toInt(), eventColorKey = "5")
            .toEditForm(beginMillis = 0L, endMillis = 3_600_000L, zone = berlin)
        assertThat(prefilled.colorKey).isEqualTo("5")
        assertThat(prefilled.color).isEqualTo(0xFF33B679.toInt())
        assertThat(prefilled.populatedFields()).contains(EventFormField.Color)
    }

    @Test
    fun `toEditForm carries a raw colour with no key`() {
        val prefilled = detail(eventColor = 0xFF8E24AA.toInt())
            .toEditForm(beginMillis = 0L, endMillis = 3_600_000L, zone = berlin)
        assertThat(prefilled.colorKey).isNull()
        assertThat(prefilled.color).isEqualTo(0xFF8E24AA.toInt())
    }

    @Test
    fun `toEditForm leaves an inheriting event without a colour`() {
        val prefilled = detail()
            .toEditForm(beginMillis = 0L, endMillis = 3_600_000L, zone = berlin)
        assertThat(prefilled.colorKey).isNull()
        assertThat(prefilled.color).isNull()
        assertThat(prefilled.populatedFields()).doesNotContain(EventFormField.Color)
    }
}
