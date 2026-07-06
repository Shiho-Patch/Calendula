package de.jeanlucmakiola.calendula.data.calendar

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.AccessLevel
import de.jeanlucmakiola.calendula.domain.AttendeeRelationship
import de.jeanlucmakiola.calendula.domain.AttendeeStatus
import de.jeanlucmakiola.calendula.domain.AttendeeType
import de.jeanlucmakiola.calendula.domain.Availability
import de.jeanlucmakiola.calendula.domain.EventStatus
import de.jeanlucmakiola.calendula.domain.Reminder
import de.jeanlucmakiola.calendula.domain.ReminderMethod
import org.junit.jupiter.api.Test

class EventDetailMapperTest {

    private fun detailReader(
        eventId: Long = 1L,
        title: String? = "Meet",
        description: String? = "Body",
        organizer: String? = "x@y",
        rrule: String? = null,
        eventColor: Any? = null,
        eventColorKey: String? = null,
        calendarColor: Int = 0xFFAABBCC.toInt(),
        dtstart: Long? = 1_000_000_000L,
        dtend: Long? = 1_000_003_600L,
        allDay: Int = 0,
        location: String? = "Berlin",
        calendarId: Long = 7L,
        status: Any? = null,
        availability: Any? = null,
        accessLevel: Any? = null,
        timezone: String? = null,
        selfStatus: Any? = null,
    ): MapColumnReader = MapColumnReader(
        EventDetailProjection.IDX_EVENT_ID to eventId,
        EventDetailProjection.IDX_TITLE to title,
        EventDetailProjection.IDX_DESCRIPTION to description,
        EventDetailProjection.IDX_ORGANIZER to organizer,
        EventDetailProjection.IDX_RRULE to rrule,
        EventDetailProjection.IDX_EVENT_COLOR to eventColor,
        EventDetailProjection.IDX_CALENDAR_COLOR to calendarColor,
        EventDetailProjection.IDX_DTSTART to dtstart,
        EventDetailProjection.IDX_DTEND to dtend,
        EventDetailProjection.IDX_ALL_DAY to allDay,
        EventDetailProjection.IDX_LOCATION to location,
        EventDetailProjection.IDX_CALENDAR_ID to calendarId,
        EventDetailProjection.IDX_STATUS to status,
        EventDetailProjection.IDX_AVAILABILITY to availability,
        EventDetailProjection.IDX_ACCESS_LEVEL to accessLevel,
        EventDetailProjection.IDX_EVENT_TIMEZONE to timezone,
        EventDetailProjection.IDX_SELF_ATTENDEE_STATUS to selfStatus,
        EventDetailProjection.IDX_EVENT_COLOR_KEY to eventColorKey,
    )

    private fun attendeeReader(
        name: String?,
        email: String?,
        status: Int,
        relationship: Int = 0,
        type: Int = 0,
    ): MapColumnReader =
        MapColumnReader(
            AttendeeProjection.IDX_NAME to name,
            AttendeeProjection.IDX_EMAIL to email,
            AttendeeProjection.IDX_STATUS to status,
            AttendeeProjection.IDX_RELATIONSHIP to relationship,
            AttendeeProjection.IDX_TYPE to type,
        )

    private fun reminderReader(minutes: Int, method: Int): MapColumnReader =
        MapColumnReader(
            ReminderProjection.IDX_MINUTES to minutes,
            ReminderProjection.IDX_METHOD to method,
        )

    private fun MapColumnReader.toDetail(
        attendees: List<de.jeanlucmakiola.calendula.domain.Attendee> = emptyList(),
        reminders: List<Reminder> = emptyList(),
    ) = toEventDetailCore(attendees, reminders)

    @Test
    fun `happy path detail maps all fields and embeds matching EventInstance`() {
        val detail = detailReader().toDetail()
        assertThat(detail).isNotNull()
        assertThat(detail!!.description).isEqualTo("Body")
        assertThat(detail.organizer).isEqualTo("x@y")
        assertThat(detail.instance.title).isEqualTo("Meet")
        assertThat(detail.instance.location).isEqualTo("Berlin")
        assertThat(detail.attendees).isEmpty()
    }

    @Test
    fun `missing title stays raw so the edit form does not inherit a placeholder`() {
        assertThat(detailReader(title = null).toDetail()!!.instance.title).isEmpty()
        assertThat(detailReader(title = "").toDetail()!!.instance.title).isEmpty()
    }

    @Test
    fun `event color falls back to calendar color when null`() {
        val detail = detailReader(eventColor = null, calendarColor = 0xFF112233.toInt())
            .toDetail()
        assertThat(detail!!.instance.color).isEqualTo(0xFF112233.toInt())
        // No own colour: the edit form must see this as "inherits".
        assertThat(detail.eventColor).isNull()
        assertThat(detail.eventColorKey).isNull()
    }

    @Test
    fun `own event color and key are surfaced apart from the resolved color`() {
        val detail = detailReader(
            eventColor = 0xFF33B679.toInt(),
            eventColorKey = "5",
            calendarColor = 0xFF112233.toInt(),
        ).toDetail()
        // Resolved display colour is the event's own, not the calendar fallback.
        assertThat(detail!!.instance.color).isEqualTo(0xFF33B679.toInt())
        assertThat(detail.eventColor).isEqualTo(0xFF33B679.toInt())
        assertThat(detail.eventColorKey).isEqualTo("5")
    }

    @Test
    fun `dtend before dtstart drops detail`() {
        val detail = detailReader(dtstart = 2000L, dtend = 1000L).toDetail()
        assertThat(detail).isNull()
    }

    @Test
    fun `pre-1970 negative dtstart is kept, not dropped (issue #34)`() {
        // A yearly birthday/anniversary anchored before the epoch has a
        // legitimately negative UTC epoch-millis DTSTART; recurring rows carry
        // no DTEND (they use DURATION), so it stays end == begin.
        val begin = -157_766_400_000L // 1965-01-01T00:00:00Z
        val detail = detailReader(dtstart = begin, dtend = null).toDetail()
        assertThat(detail).isNotNull()
        assertThat(detail!!.instance.start.toEpochMilliseconds()).isEqualTo(begin)
        assertThat(detail.instance.end.toEpochMilliseconds()).isEqualTo(begin)
    }

    @Test
    fun `absent dtstart drops detail`() {
        assertThat(detailReader(dtstart = null).toDetail()).isNull()
    }

    @Test
    fun `rrule passes through when present`() {
        val detail = detailReader(rrule = "FREQ=WEEKLY;BYDAY=MO").toDetail()
        assertThat(detail!!.rrule).isEqualTo("FREQ=WEEKLY;BYDAY=MO")
    }

    // Raw CalendarContract.Attendees status integer constants from the Android
    // source (kept inline so the test doesn't depend on the mockable.jar's
    // possibly-stubbed constants):
    // ACCEPTED=1, DECLINED=2, INVITED=3, TENTATIVE=4, NONE=0
    @Test
    fun `attendee status maps known integer codes`() {
        assertThat(attendeeReader("A", "a@x", 1).toAttendee().status)
            .isEqualTo(AttendeeStatus.Accepted)
        assertThat(attendeeReader("B", "b@x", 2).toAttendee().status)
            .isEqualTo(AttendeeStatus.Declined)
        assertThat(attendeeReader("C", "c@x", 4).toAttendee().status)
            .isEqualTo(AttendeeStatus.Tentative)
        assertThat(attendeeReader("D", "d@x", 3).toAttendee().status)
            .isEqualTo(AttendeeStatus.NeedsAction)
        assertThat(attendeeReader("E", "e@x", 0).toAttendee().status)
            .isEqualTo(AttendeeStatus.Unknown)
        assertThat(attendeeReader("F", "f@x", 99).toAttendee().status)
            .isEqualTo(AttendeeStatus.Unknown)
    }

    @Test
    fun `attendee with null name maps to empty string`() {
        val a = attendeeReader(null, "alice@x", 1).toAttendee()
        assertThat(a.name).isEqualTo("")
    }

    @Test
    fun `attendee email passes through nullably`() {
        assertThat(attendeeReader("A", null, 1).toAttendee().email).isNull()
        assertThat(attendeeReader("A", "x@y", 1).toAttendee().email).isEqualTo("x@y")
    }

    // RELATIONSHIP: NONE=0, ATTENDEE=1, ORGANIZER=2, PERFORMER=3, SPEAKER=4
    @Test
    fun `attendee relationship maps known integer codes`() {
        assertThat(attendeeReader("A", "a@x", 1, relationship = 2).toAttendee().relationship)
            .isEqualTo(AttendeeRelationship.Organizer)
        assertThat(attendeeReader("A", "a@x", 1, relationship = 1).toAttendee().relationship)
            .isEqualTo(AttendeeRelationship.Attendee)
        assertThat(attendeeReader("A", "a@x", 1, relationship = 0).toAttendee().relationship)
            .isEqualTo(AttendeeRelationship.None)
    }

    // TYPE: NONE=0, REQUIRED=1, OPTIONAL=2, RESOURCE=3
    @Test
    fun `attendee type maps known integer codes`() {
        assertThat(attendeeReader("A", "a@x", 1, type = 1).toAttendee().type)
            .isEqualTo(AttendeeType.Required)
        assertThat(attendeeReader("A", "a@x", 1, type = 2).toAttendee().type)
            .isEqualTo(AttendeeType.Optional)
        assertThat(attendeeReader("A", "a@x", 1, type = 3).toAttendee().type)
            .isEqualTo(AttendeeType.Resource)
    }

    // STATUS: TENTATIVE=0, CONFIRMED=1, CANCELED=2
    @Test
    fun `event status null maps to confirmed, codes map through`() {
        assertThat(detailReader(status = null).toDetail()!!.status).isEqualTo(EventStatus.Confirmed)
        assertThat(detailReader(status = 0).toDetail()!!.status).isEqualTo(EventStatus.Tentative)
        assertThat(detailReader(status = 1).toDetail()!!.status).isEqualTo(EventStatus.Confirmed)
        assertThat(detailReader(status = 2).toDetail()!!.status).isEqualTo(EventStatus.Cancelled)
    }

    // AVAILABILITY: BUSY=0, FREE=1, TENTATIVE=2
    @Test
    fun `availability null or busy maps to Busy, free maps to Free`() {
        assertThat(detailReader(availability = null).toDetail()!!.availability)
            .isEqualTo(Availability.Busy)
        assertThat(detailReader(availability = 0).toDetail()!!.availability)
            .isEqualTo(Availability.Busy)
        assertThat(detailReader(availability = 1).toDetail()!!.availability)
            .isEqualTo(Availability.Free)
    }

    // ACCESS_LEVEL: DEFAULT=0, CONFIDENTIAL=1, PRIVATE=2, PUBLIC=3
    @Test
    fun `access level maps known integer codes, null is Default`() {
        assertThat(detailReader(accessLevel = null).toDetail()!!.accessLevel)
            .isEqualTo(AccessLevel.Default)
        assertThat(detailReader(accessLevel = 1).toDetail()!!.accessLevel)
            .isEqualTo(AccessLevel.Confidential)
        assertThat(detailReader(accessLevel = 2).toDetail()!!.accessLevel)
            .isEqualTo(AccessLevel.Private)
    }

    @Test
    fun `event timezone and self status pass through`() {
        val detail = detailReader(timezone = "Europe/Berlin", selfStatus = 1).toDetail()
        assertThat(detail!!.eventTimezone).isEqualTo("Europe/Berlin")
        assertThat(detail.selfStatus).isEqualTo(AttendeeStatus.Accepted)
    }

    @Test
    fun `reminders pass through to the detail`() {
        val reminders = listOf(Reminder(10, ReminderMethod.Alert))
        val detail = detailReader().toDetail(reminders = reminders)
        assertThat(detail!!.reminders).isEqualTo(reminders)
    }

    // METHOD: DEFAULT=0, ALERT=1, EMAIL=2, SMS=3, ALARM=4
    @Test
    fun `reminder maps minutes and method codes`() {
        assertThat(reminderReader(10, 1).toReminder())
            .isEqualTo(Reminder(10, ReminderMethod.Alert))
        assertThat(reminderReader(60, 2).toReminder())
            .isEqualTo(Reminder(60, ReminderMethod.Email))
        assertThat(reminderReader(0, 0).toReminder())
            .isEqualTo(Reminder(0, ReminderMethod.Default))
    }
}
