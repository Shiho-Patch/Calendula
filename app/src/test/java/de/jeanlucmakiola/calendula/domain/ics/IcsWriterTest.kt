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

class IcsWriterTest {

    private val writer = IcsWriter(prodId = "-//Test//Test//EN")
    private val stamp = LocalDateTime(2026, 6, 18, 9, 30, 0).toInstant(TimeZone.UTC)

    private fun lines(events: List<IcsEvent>): List<String> =
        writer.writeCalendar(events, stamp).split(ICS_CRLF)

    private fun instantUtc(y: Int, mo: Int, d: Int, h: Int, mi: Int): Instant =
        LocalDateTime(y, mo, d, h, mi, 0).toInstant(TimeZone.UTC)

    @Test
    fun `calendar is wrapped with the required header and CRLF endings`() {
        val out = writer.writeCalendar(emptyList(), stamp)
        assertThat(out).startsWith("BEGIN:VCALENDAR\r\n")
        assertThat(out).endsWith("END:VCALENDAR\r\n")
        assertThat(out).contains("VERSION:2.0\r\n")
        assertThat(out).contains("PRODID:-//Test//Test//EN\r\n")
    }

    @Test
    fun `timed one-off event writes UTC instants with a Z suffix`() {
        val event = IcsEvent(
            uid = "u1@calendula",
            summary = "Standup",
            start = instantUtc(2026, 6, 18, 13, 0),
            end = instantUtc(2026, 6, 18, 13, 30),
            isAllDay = false,
            zoneId = "Europe/Berlin",
        )
        val l = lines(listOf(event))
        assertThat(l).contains("DTSTART:20260618T130000Z")
        assertThat(l).contains("DTEND:20260618T133000Z")
        assertThat(l).contains("UID:u1@calendula")
        assertThat(l).contains("STATUS:CONFIRMED")
        assertThat(l).contains("TRANSP:OPAQUE")
    }

    @Test
    fun `recurring timed event anchors to wall-clock with TZID`() {
        // 13:00 UTC == 15:00 Berlin in summer; the series must read 15:00 local.
        val event = IcsEvent(
            uid = "u2@calendula",
            summary = "Weekly",
            start = instantUtc(2026, 6, 18, 13, 0),
            end = instantUtc(2026, 6, 18, 14, 0),
            isAllDay = false,
            zoneId = "Europe/Berlin",
            recurrenceRule = "FREQ=WEEKLY",
        )
        val l = lines(listOf(event))
        assertThat(l).contains("DTSTART;TZID=Europe/Berlin:20260618T150000")
        assertThat(l).contains("DTEND;TZID=Europe/Berlin:20260618T160000")
        assertThat(l).contains("RRULE:FREQ=WEEKLY")
    }

    @Test
    fun `recurring event with an unknown zone falls back to UTC instants`() {
        val event = IcsEvent(
            uid = "u3@calendula",
            summary = "Weekly",
            start = instantUtc(2026, 6, 18, 13, 0),
            end = instantUtc(2026, 6, 18, 14, 0),
            isAllDay = false,
            zoneId = "Mars/Olympus",
            recurrenceRule = "FREQ=WEEKLY",
        )
        val l = lines(listOf(event))
        assertThat(l).contains("DTSTART:20260618T130000Z")
        assertThat(l).contains("DTEND:20260618T140000Z")
    }

    @Test
    fun `all-day event writes exclusive DATE values without a zone`() {
        val start = LocalDate(2026, 6, 18).atStartOfDayIn(TimeZone.UTC)
        val end = LocalDate(2026, 6, 19).atStartOfDayIn(TimeZone.UTC)
        val event = IcsEvent(
            uid = "u4@calendula",
            summary = "Holiday",
            start = start,
            end = end,
            isAllDay = true,
            zoneId = "UTC",
        )
        val l = lines(listOf(event))
        assertThat(l).contains("DTSTART;VALUE=DATE:20260618")
        assertThat(l).contains("DTEND;VALUE=DATE:20260619")
    }

    @Test
    fun `reminders become VALARM blocks with before-start triggers`() {
        val event = IcsEvent(
            uid = "u5@calendula",
            summary = "Meeting",
            start = instantUtc(2026, 6, 18, 13, 0),
            end = instantUtc(2026, 6, 18, 14, 0),
            isAllDay = false,
            zoneId = "UTC",
            reminderMinutes = listOf(15, 0, 15), // duplicate is dropped
        )
        val out = writer.writeCalendar(listOf(event), stamp)
        val l = out.split(ICS_CRLF)
        assertThat(l.count { it == "BEGIN:VALARM" }).isEqualTo(2)
        assertThat(l).contains("TRIGGER:-PT15M")
        assertThat(l).contains("TRIGGER:PT0M")
        assertThat(l).contains("ACTION:DISPLAY")
    }

    @Test
    fun `text fields and the calendar name are escaped`() {
        val event = IcsEvent(
            uid = "u6@calendula",
            summary = "Lunch; with, notes",
            start = instantUtc(2026, 6, 18, 13, 0),
            end = instantUtc(2026, 6, 18, 14, 0),
            isAllDay = false,
            zoneId = "UTC",
            location = "Cafe\\Bar",
            availability = Availability.Free,
            status = EventStatus.Tentative,
            calendarName = "Work, Personal",
        )
        val l = lines(listOf(event))
        assertThat(l).contains("SUMMARY:Lunch\\; with\\, notes")
        assertThat(l).contains("LOCATION:Cafe\\\\Bar")
        assertThat(l).contains("STATUS:TENTATIVE")
        assertThat(l).contains("TRANSP:TRANSPARENT")
        assertThat(l).contains("X-CALENDULA-CALENDAR:Work\\, Personal")
    }

    @Test
    fun `existing uid is kept and a missing one is synthesised stably`() {
        assertThat(deriveIcsUid("real-uid@host", 7, 1000)).isEqualTo("real-uid@host")
        assertThat(deriveIcsUid(null, 7, 1000)).isEqualTo("7-1000@calendula")
        assertThat(deriveIcsUid("  ", 7, 1000)).isEqualTo("7-1000@calendula")
        // Stable across calls — a re-export of the same row yields the same UID.
        assertThat(deriveIcsUid(null, 7, 1000)).isEqualTo(deriveIcsUid(null, 7, 1000))
    }

    @Test
    fun `a timed series writes its exclusions as UTC stamps`() {
        val event = IcsEvent(
            uid = "u9@calendula",
            summary = "Weekly",
            start = instantUtc(2026, 6, 18, 13, 0),
            end = instantUtc(2026, 6, 18, 13, 30),
            isAllDay = false,
            zoneId = "UTC",
            recurrenceRule = "FREQ=WEEKLY",
            exDates = listOf("20260625T130000Z", "20260702T130000Z"),
        )

        assertThat(lines(listOf(event)))
            .contains("EXDATE:20260625T130000Z,20260702T130000Z")
    }

    @Test
    fun `an all-day series marks its exclusions VALUE=DATE`() {
        val start = LocalDate(2026, 6, 18).atStartOfDayIn(TimeZone.UTC)
        val event = IcsEvent(
            uid = "u10@calendula",
            summary = "Holiday",
            start = start,
            end = LocalDate(2026, 6, 19).atStartOfDayIn(TimeZone.UTC),
            isAllDay = true,
            zoneId = "UTC",
            recurrenceRule = "FREQ=YEARLY",
            exDates = listOf("20270618"),
        )

        // RFC 5545 ties EXDATE's value type to DTSTART's; a bare day code
        // without VALUE=DATE reads as a malformed DATE-TIME.
        assertThat(lines(listOf(event))).contains("EXDATE;VALUE=DATE:20270618")
    }

    @Test
    fun `a one-off event never writes an EXDATE`() {
        val event = IcsEvent(
            uid = "u11@calendula",
            summary = "Standup",
            start = instantUtc(2026, 6, 18, 13, 0),
            end = instantUtc(2026, 6, 18, 13, 30),
            isAllDay = false,
            zoneId = "UTC",
            exDates = listOf("20260625T130000Z"),
        )

        assertThat(lines(listOf(event)).none { it.startsWith("EXDATE") }).isTrue()
    }
}
