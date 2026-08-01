package de.jeanlucmakiola.calendula.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Test

class EventShiftTest {

    private val berlin = TimeZone.of("Europe/Berlin")
    private val newYork = TimeZone.of("America/New_York")

    private fun form(
        start: LocalDateTime = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(10, 0)),
        end: LocalDateTime = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(11, 0)),
        timezone: String? = null,
        isAllDay: Boolean = false,
    ) = EventForm(
        calendarId = 1L,
        title = "Standup",
        isAllDay = isAllDay,
        start = start,
        end = end,
        timezone = timezone,
    )

    @Test
    fun `shifting to a new start keeps the length`() {
        val original = form()
        val target = LocalDateTime(LocalDate(2026, 6, 12), LocalTime(14, 30)).toInstant(berlin)

        val moved = original.shiftedTo(target, berlin)

        assertThat(moved.start).isEqualTo(LocalDateTime(LocalDate(2026, 6, 12), LocalTime(14, 30)))
        assertThat(moved.end).isEqualTo(LocalDateTime(LocalDate(2026, 6, 12), LocalTime(15, 30)))
    }

    @Test
    fun `a pinned event is re-derived in its own zone, not the device's`() {
        // 09:00 in New York, opened on a Berlin device.
        val original = form(
            start = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(9, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(10, 0)),
            timezone = "America/New_York",
        )
        // The grid says "15:30 Berlin", which is 09:30 in New York.
        val target = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(15, 30)).toInstant(berlin)

        val moved = original.shiftedTo(target, berlin)

        assertThat(moved.timezone).isEqualTo("America/New_York")
        assertThat(moved.start).isEqualTo(LocalDateTime(LocalDate(2026, 6, 11), LocalTime(9, 30)))
        assertThat(moved.end).isEqualTo(LocalDateTime(LocalDate(2026, 6, 11), LocalTime(10, 30)))
    }

    @Test
    fun `the instant duration survives a shift across spring forward`() {
        // 2026-03-29 02:00 CET is when Berlin skips to 03:00.
        val original = form(
            start = LocalDateTime(LocalDate(2026, 3, 28), LocalTime(23, 0)),
            end = LocalDateTime(LocalDate(2026, 3, 29), LocalTime(1, 0)),
        )
        val target = LocalDateTime(LocalDate(2026, 3, 29), LocalTime(1, 0)).toInstant(berlin)

        val moved = original.shiftedTo(target, berlin)

        // Two real hours from 01:00 CET lands at 04:00 CEST, not 03:00: the wall
        // clock stretches because the hour in between does not exist.
        assertThat(moved.start).isEqualTo(LocalDateTime(LocalDate(2026, 3, 29), LocalTime(1, 0)))
        assertThat(moved.end).isEqualTo(LocalDateTime(LocalDate(2026, 3, 29), LocalTime(4, 0)))
        assertThat(moved.end.toInstant(berlin) - moved.start.toInstant(berlin))
            .isEqualTo(original.end.toInstant(berlin) - original.start.toInstant(berlin))
    }

    @Test
    fun `a drop into the spring-forward gap resolves forward by the missing hour`() {
        val original = form()
        // 02:30 does not exist on 2026-03-29 in Berlin.
        val target = LocalDateTime(LocalDate(2026, 3, 29), LocalTime(2, 30)).toInstant(berlin)

        val moved = original.shiftedTo(target, berlin)

        assertThat(moved.start).isEqualTo(LocalDateTime(LocalDate(2026, 3, 29), LocalTime(3, 30)))
    }

    @Test
    fun `a zero-distance drop leaves the form untouched`() {
        val original = form()
        assertThat(original.shiftedTo(original.start.toInstant(berlin), berlin)).isEqualTo(original)
    }

    @Test
    fun `an unparseable pinned zone falls back to the device, like the write path`() {
        val original = form(timezone = "Mars/Olympus")
        val target = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(12, 0)).toInstant(newYork)

        val moved = original.shiftedTo(target, newYork)

        assertThat(moved.start).isEqualTo(LocalDateTime(LocalDate(2026, 6, 11), LocalTime(12, 0)))
    }

    @Test
    fun `an all-day event ignores shiftedTo and moves by whole days instead`() {
        val allDay = form(isAllDay = true)
        val target = LocalDateTime(LocalDate(2026, 7, 1), LocalTime(3, 0)).toInstant(berlin)

        assertThat(allDay.shiftedTo(target, berlin)).isEqualTo(allDay)

        val moved = allDay.shiftedByDays(3, berlin)
        assertThat(moved.start.date).isEqualTo(LocalDate(2026, 6, 14))
        assertThat(moved.end.date).isEqualTo(LocalDate(2026, 6, 14))
        // The placeholder times exist only for a switch back to timed; untouched.
        assertThat(moved.start.time).isEqualTo(allDay.start.time)
    }

    @Test
    fun `a multi-day event keeps its span when shifted by days`() {
        val multiDay = form(
            start = LocalDateTime(LocalDate(2026, 6, 11), LocalTime(22, 0)),
            end = LocalDateTime(LocalDate(2026, 6, 13), LocalTime(2, 0)),
        )

        val moved = multiDay.shiftedByDays(-2, berlin)

        assertThat(moved.start).isEqualTo(LocalDateTime(LocalDate(2026, 6, 9), LocalTime(22, 0)))
        assertThat(moved.end).isEqualTo(LocalDateTime(LocalDate(2026, 6, 11), LocalTime(2, 0)))
    }

    @Test
    fun `a timed shift onto a DST changeover keeps the real length, not the wall clock`() {
        // 22:00 Sat -> 04:00 Sun is six real hours; the target Sunday is the one
        // Berlin springs forward on. Keeping wall clock would write a five-hour
        // DURATION for the whole series.
        val overnight = form(
            start = LocalDateTime(LocalDate(2026, 3, 21), LocalTime(22, 0)),
            end = LocalDateTime(LocalDate(2026, 3, 22), LocalTime(4, 0)),
        )

        val moved = overnight.shiftedByDays(7, berlin)

        assertThat(moved.start).isEqualTo(LocalDateTime(LocalDate(2026, 3, 28), LocalTime(22, 0)))
        assertThat(moved.end.toInstant(berlin) - moved.start.toInstant(berlin))
            .isEqualTo(overnight.end.toInstant(berlin) - overnight.start.toInstant(berlin))
        // The wall-clock end therefore lands an hour later than a naive +7 days.
        assertThat(moved.end).isEqualTo(LocalDateTime(LocalDate(2026, 3, 29), LocalTime(5, 0)))
    }

    @Test
    fun `shifting by no days is a no-op`() {
        val original = form()
        assertThat(original.shiftedByDays(0, berlin)).isEqualTo(original)
    }
}
