package de.jeanlucmakiola.calendula.data.calendar

import android.provider.CalendarContract
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.EventResponse
import de.jeanlucmakiola.calendula.domain.isDeclined
import kotlin.time.Instant
import org.junit.jupiter.api.Test

class InstanceMapperTest {

    private fun reader(
        instanceId: Long = 10L,
        eventId: Long = 1L,
        calendarId: Long = 1L,
        title: String? = "Meet",
        begin: Long = 1_000_000_000L,
        end: Long = 1_000_003_600L,
        allDay: Int = 0,
        eventColor: Any? = null,
        calendarColor: Int = 0xFFAABBCC.toInt(),
        location: String? = null,
        selfAttendeeStatus: Int = CalendarContract.Attendees.ATTENDEE_STATUS_NONE,
    ): MapColumnReader = MapColumnReader(
        InstanceProjection.IDX_INSTANCE_ID to instanceId,
        InstanceProjection.IDX_EVENT_ID to eventId,
        InstanceProjection.IDX_CALENDAR_ID to calendarId,
        InstanceProjection.IDX_TITLE to title,
        InstanceProjection.IDX_BEGIN to begin,
        InstanceProjection.IDX_END to end,
        InstanceProjection.IDX_ALL_DAY to allDay,
        InstanceProjection.IDX_EVENT_COLOR to eventColor,
        InstanceProjection.IDX_CALENDAR_COLOR to calendarColor,
        InstanceProjection.IDX_LOCATION to location,
        InstanceProjection.IDX_SELF_ATTENDEE_STATUS to selfAttendeeStatus,
    )

    @Test
    fun `happy path - non-allday event`() {
        val inst = reader().toEventInstance()
        assertThat(inst).isNotNull()
        assertThat(inst!!.title).isEqualTo("Meet")
        assertThat(inst.isAllDay).isFalse()
        assertThat(inst.start).isEqualTo(Instant.fromEpochMilliseconds(1_000_000_000L))
        assertThat(inst.end).isEqualTo(Instant.fromEpochMilliseconds(1_000_003_600L))
    }

    @Test
    fun `event color falls back to calendar color when null`() {
        val inst = reader(eventColor = null, calendarColor = 0xFF112233.toInt()).toEventInstance()
        assertThat(inst!!.color).isEqualTo(0xFF112233.toInt())
    }

    @Test
    fun `event color wins over calendar color when present`() {
        val inst = reader(
            eventColor = 0xFFDEADBE.toInt(),
            calendarColor = 0xFF112233.toInt(),
        ).toEventInstance()
        assertThat(inst!!.color).isEqualTo(0xFFDEADBE.toInt())
    }

    @Test
    fun `null title is left blank for the UI to fill in`() {
        val inst = reader(title = null).toEventInstance()
        assertThat(inst!!.title).isEmpty()
    }

    @Test
    fun `empty title is left blank for the UI to fill in`() {
        val inst = reader(title = "").toEventInstance()
        assertThat(inst!!.title).isEmpty()
    }

    @Test
    fun `dtend before dtstart drops the row`() {
        val inst = reader(begin = 2000L, end = 1000L).toEventInstance()
        assertThat(inst).isNull()
    }

    @Test
    fun `dtstart before unix epoch drops the row`() {
        val inst = reader(begin = -1L, end = 1000L).toEventInstance()
        assertThat(inst).isNull()
    }

    @Test
    fun `all-day flag 1 maps to true`() {
        val inst = reader(allDay = 1).toEventInstance()
        assertThat(inst!!.isAllDay).isTrue()
    }

    @Test
    fun `location passes through when present`() {
        val inst = reader(location = "Berlin").toEventInstance()
        assertThat(inst!!.location).isEqualTo("Berlin")
    }

    @Test
    fun `a declined invitation is marked, any other answer is not`() {
        assertThat(reader().toEventInstance()!!.isDeclined).isFalse()
        assertThat(
            reader(selfAttendeeStatus = CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED)
                .toEventInstance()!!.isDeclined,
        ).isTrue()
        listOf(
            CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED,
            CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE,
            CalendarContract.Attendees.ATTENDEE_STATUS_INVITED,
        ).forEach { status ->
            assertThat(reader(selfAttendeeStatus = status).toEventInstance()!!.isDeclined).isFalse()
        }
    }

    @Test
    fun `an unanswered invitation reads as invited, an answered one does not`() {
        assertThat(
            reader(selfAttendeeStatus = CalendarContract.Attendees.ATTENDEE_STATUS_INVITED)
                .toEventInstance()!!.response,
        ).isEqualTo(EventResponse.Invited)
        mapOf(
            CalendarContract.Attendees.ATTENDEE_STATUS_NONE to EventResponse.Going,
            CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED to EventResponse.Going,
            // "Maybe" is still an answer, so it keeps the solid chip (#230).
            CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE to EventResponse.Going,
            CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED to EventResponse.Declined,
        ).forEach { (status, expected) ->
            assertThat(reader(selfAttendeeStatus = status).toEventInstance()!!.response)
                .isEqualTo(expected)
        }
    }
}
