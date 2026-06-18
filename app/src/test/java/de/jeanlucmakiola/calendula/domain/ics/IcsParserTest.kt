package de.jeanlucmakiola.calendula.domain.ics

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.Availability
import de.jeanlucmakiola.calendula.domain.EventStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class IcsParserTest {

    private val parser = IcsParser(deviceZone = TimeZone.of("Europe/Berlin"))
    private val writer = IcsWriter()
    private val stamp = LocalDateTime(2026, 6, 18, 9, 30, 0).toInstant(TimeZone.UTC)

    private fun roundTrip(event: IcsEvent): ParsedIcsEvent {
        val text = writer.writeCalendar(listOf(event), stamp)
        val result = parser.parse(text)
        assertThat(result.events).hasSize(1)
        return result.events.single()
    }

    private fun instantUtc(y: Int, mo: Int, d: Int, h: Int, mi: Int): Instant =
        LocalDateTime(y, mo, d, h, mi, 0).toInstant(TimeZone.UTC)

    @Test
    fun `round-trips a timed one-off event`() {
        val event = IcsEvent(
            uid = "u1@calendula",
            summary = "Lunch; with, friends",
            start = instantUtc(2026, 6, 18, 11, 0),
            end = instantUtc(2026, 6, 18, 12, 0),
            isAllDay = false,
            zoneId = "Europe/Berlin",
            location = "Café",
            availability = Availability.Free,
            status = EventStatus.Tentative,
        )
        val parsed = roundTrip(event)
        assertThat(parsed.uid).isEqualTo("u1@calendula")
        assertThat(parsed.summary).isEqualTo("Lunch; with, friends")
        assertThat(parsed.start).isEqualTo(event.start)
        assertThat(parsed.end).isEqualTo(event.end)
        assertThat(parsed.isAllDay).isFalse()
        assertThat(parsed.location).isEqualTo("Café")
        assertThat(parsed.availability).isEqualTo(Availability.Free)
        assertThat(parsed.status).isEqualTo(EventStatus.Tentative)
    }

    @Test
    fun `round-trips a recurring TZID event to the same instant`() {
        val event = IcsEvent(
            uid = "u2@calendula",
            summary = "Weekly",
            start = instantUtc(2026, 6, 18, 13, 0),
            end = instantUtc(2026, 6, 18, 14, 0),
            isAllDay = false,
            zoneId = "Europe/Berlin",
            recurrenceRule = "FREQ=WEEKLY",
        )
        val parsed = roundTrip(event)
        assertThat(parsed.start).isEqualTo(event.start)
        assertThat(parsed.end).isEqualTo(event.end)
        assertThat(parsed.zoneId).isEqualTo("Europe/Berlin")
        assertThat(parsed.recurrenceRule).isEqualTo("FREQ=WEEKLY")
    }

    @Test
    fun `round-trips an all-day event`() {
        val event = IcsEvent(
            uid = "u3@calendula",
            summary = "Holiday",
            start = LocalDate(2026, 6, 18).atStartOfDayIn(TimeZone.UTC),
            end = LocalDate(2026, 6, 19).atStartOfDayIn(TimeZone.UTC),
            isAllDay = true,
            zoneId = "UTC",
        )
        val parsed = roundTrip(event)
        assertThat(parsed.isAllDay).isTrue()
        assertThat(parsed.start).isEqualTo(event.start)
        assertThat(parsed.end).isEqualTo(event.end)
    }

    @Test
    fun `round-trips reminders as before-start lead minutes`() {
        val event = IcsEvent(
            uid = "u4@calendula",
            summary = "Meeting",
            start = instantUtc(2026, 6, 18, 13, 0),
            end = instantUtc(2026, 6, 18, 14, 0),
            isAllDay = false,
            zoneId = "UTC",
            reminderMinutes = listOf(15, 0),
        )
        val parsed = roundTrip(event)
        assertThat(parsed.reminderMinutes).containsExactly(15, 0)
    }

    @Test
    fun `tolerates folded lines and a missing UID`() {
        val ics = buildString {
            append("BEGIN:VCALENDAR\r\n")
            append("VERSION:2.0\r\n")
            append("BEGIN:VEVENT\r\n")
            // Folded DESCRIPTION (continuation line begins with a space).
            append("DESCRIPTION:This is a long descriptio\r\n n that was folded\r\n")
            append("SUMMARY:No UID here\r\n")
            append("DTSTART:20260618T090000Z\r\n")
            append("DTEND:20260618T100000Z\r\n")
            append("END:VEVENT\r\n")
            append("END:VCALENDAR\r\n")
        }
        val parsed = parser.parse(ics).events.single()
        assertThat(parsed.uid).isNull()
        assertThat(parsed.description).isEqualTo("This is a long description that was folded")
        assertThat(parsed.summary).isEqualTo("No UID here")
    }

    @Test
    fun `skips a RECURRENCE-ID override and reports it`() {
        val ics = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:x\r\n" +
            "RECURRENCE-ID:20260618T090000Z\r\nDTSTART:20260618T090000Z\r\n" +
            "SUMMARY:Override\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        val result = parser.parse(ics)
        assertThat(result.events).isEmpty()
        assertThat(result.warnings).contains(IcsParseWarning.ModifiedOccurrenceSkipped)
    }

    @Test
    fun `reports ignored attendees but still imports the event`() {
        val ics = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:y\r\n" +
            "DTSTART:20260618T090000Z\r\nSUMMARY:Has guests\r\n" +
            "ATTENDEE;CN=Bob:mailto:bob@example.com\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        val result = parser.parse(ics)
        assertThat(result.events).hasSize(1)
        assertThat(result.warnings).contains(IcsParseWarning.AttendeesIgnored)
    }

    @Test
    fun `parses multiple events and carries the calendar name`() {
        val events = listOf(
            IcsEvent("a@c", "One", instantUtc(2026, 6, 18, 9, 0), instantUtc(2026, 6, 18, 10, 0),
                false, "UTC", calendarName = "Personal"),
            IcsEvent("b@c", "Two", instantUtc(2026, 6, 19, 9, 0), instantUtc(2026, 6, 19, 10, 0),
                false, "UTC", calendarName = "Personal"),
        )
        val text = writer.writeCalendar(events, stamp)
        val result = parser.parse(text)
        assertThat(result.events).hasSize(2)
        assertThat(result.events.map { it.calendarName }).containsExactly("Personal", "Personal")
    }

    @Test
    fun `a malformed event does not sink the rest of the file`() {
        // First VEVENT has no DTSTART (skipped); second is valid.
        val ics = "BEGIN:VCALENDAR\r\n" +
            "BEGIN:VEVENT\r\nUID:bad\r\nSUMMARY:No start\r\nEND:VEVENT\r\n" +
            "BEGIN:VEVENT\r\nUID:good\r\nDTSTART:20260618T090000Z\r\nSUMMARY:Fine\r\nEND:VEVENT\r\n" +
            "END:VCALENDAR\r\n"
        val result = parser.parse(ics)
        assertThat(result.events.map { it.uid }).containsExactly("good")
        assertThat(result.warnings).contains(IcsParseWarning.EventWithoutStartSkipped)
    }
}
