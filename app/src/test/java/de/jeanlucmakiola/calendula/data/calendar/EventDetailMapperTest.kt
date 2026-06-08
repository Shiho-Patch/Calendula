package de.jeanlucmakiola.calendula.data.calendar

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.AttendeeStatus
import org.junit.jupiter.api.Test

class EventDetailMapperTest {

    private fun detailReader(
        eventId: Long = 1L,
        title: String? = "Meet",
        description: String? = "Body",
        organizer: String? = "x@y",
        rrule: String? = null,
        eventColor: Any? = null,
        calendarColor: Int = 0xFFAABBCC.toInt(),
        dtstart: Long = 1_000_000_000L,
        dtend: Long = 1_000_003_600L,
        allDay: Int = 0,
        location: String? = "Berlin",
        calendarId: Long = 7L,
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
    )

    private fun attendeeReader(name: String?, email: String?, status: Int): MapColumnReader =
        MapColumnReader(
            AttendeeProjection.IDX_NAME to name,
            AttendeeProjection.IDX_EMAIL to email,
            AttendeeProjection.IDX_STATUS to status,
        )

    @Test
    fun `happy path detail maps all fields and embeds matching EventInstance`() {
        val detail = detailReader().toEventDetailCore(attendees = emptyList())
        assertThat(detail).isNotNull()
        assertThat(detail!!.description).isEqualTo("Body")
        assertThat(detail.organizer).isEqualTo("x@y")
        assertThat(detail.instance.title).isEqualTo("Meet")
        assertThat(detail.instance.location).isEqualTo("Berlin")
        assertThat(detail.attendees).isEmpty()
    }

    @Test
    fun `event color falls back to calendar color when null`() {
        val detail = detailReader(eventColor = null, calendarColor = 0xFF112233.toInt())
            .toEventDetailCore(attendees = emptyList())
        assertThat(detail!!.instance.color).isEqualTo(0xFF112233.toInt())
    }

    @Test
    fun `dtend before dtstart drops detail`() {
        val detail = detailReader(dtstart = 2000L, dtend = 1000L)
            .toEventDetailCore(attendees = emptyList())
        assertThat(detail).isNull()
    }

    @Test
    fun `rrule passes through when present`() {
        val detail = detailReader(rrule = "FREQ=WEEKLY;BYDAY=MO")
            .toEventDetailCore(attendees = emptyList())
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
}
