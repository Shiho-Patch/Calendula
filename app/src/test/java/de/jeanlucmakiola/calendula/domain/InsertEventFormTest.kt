package de.jeanlucmakiola.calendula.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class InsertEventFormTest {

    private val zone = TimeZone.UTC

    // A fixed "now" at 2026-01-15T10:20:00Z; the next full hour is 11:00.
    private val now = LocalDateTime(2026, 1, 15, 10, 20).toInstant(zone)

    private fun build(
        beginMillis: Long? = null,
        endMillis: Long? = null,
        isAllDay: Boolean = false,
        title: String? = null,
        description: String? = null,
        location: String? = null,
        rrule: String? = null,
    ): EventForm = buildInsertEventForm(
        beginMillis = beginMillis,
        endMillis = endMillis,
        isAllDay = isAllDay,
        title = title,
        description = description,
        location = location,
        rrule = rrule,
        zone = zone,
        now = now,
    )

    private fun millis(dateTime: LocalDateTime): Long = dateTime.toInstant(zone).toEpochMilliseconds()

    @Test
    fun `timed event maps all provided fields`() {
        val start = LocalDateTime(2026, 3, 1, 14, 30)
        val end = LocalDateTime(2026, 3, 1, 15, 45)
        val form = build(
            beginMillis = millis(start),
            endMillis = millis(end),
            title = "Standup",
            description = "Daily",
            location = "Room 1",
            rrule = "FREQ=DAILY",
        )
        assertThat(form.calendarId).isNull() // resolves to last-used / first-writable
        assertThat(form.isAllDay).isFalse()
        assertThat(form.start).isEqualTo(start)
        assertThat(form.end).isEqualTo(end)
        assertThat(form.title).isEqualTo("Standup")
        assertThat(form.description).isEqualTo("Daily")
        assertThat(form.location).isEqualTo("Room 1")
        assertThat(form.rrule).isEqualTo("FREQ=DAILY")
    }

    @Test
    fun `missing times default to the next full hour and a one-hour duration`() {
        val form = build(title = "Quick note")
        assertThat(form.start).isEqualTo(LocalDateTime(2026, 1, 15, 11, 0))
        assertThat(form.end).isEqualTo(LocalDateTime(2026, 1, 15, 12, 0))
        assertThat(form.title).isEqualTo("Quick note")
    }

    @Test
    fun `timed event with only a start gets a one-hour end`() {
        val start = LocalDateTime(2026, 5, 2, 9, 0)
        val form = build(beginMillis = millis(start))
        assertThat(form.start).isEqualTo(start)
        assertThat(form.end).isEqualTo(LocalDateTime(2026, 5, 2, 10, 0))
    }

    @Test
    fun `an end before the start is ignored and falls back to plus one hour`() {
        val start = LocalDateTime(2026, 5, 2, 9, 0)
        val badEnd = LocalDateTime(2026, 5, 2, 8, 0)
        val form = build(beginMillis = millis(start), endMillis = millis(badEnd))
        assertThat(form.end).isEqualTo(LocalDateTime(2026, 5, 2, 10, 0))
    }

    @Test
    fun `all-day event uses UTC dates and decrements the exclusive end`() {
        val startMillis = LocalDate(2026, 1, 1).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        val endExclusive = LocalDate(2026, 1, 3).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        val form = build(beginMillis = startMillis, endMillis = endExclusive, isAllDay = true, title = "Trip")
        assertThat(form.isAllDay).isTrue()
        assertThat(form.start.date).isEqualTo(LocalDate(2026, 1, 1))
        assertThat(form.end.date).isEqualTo(LocalDate(2026, 1, 2))
        // Placeholder wall-clock times survive a switch back to a timed event.
        assertThat(form.start.time).isEqualTo(LocalTime(9, 0))
        assertThat(form.end.time).isEqualTo(LocalTime(10, 0))
    }

    @Test
    fun `all-day event without an end is a single day`() {
        val startMillis = LocalDate(2026, 6, 10).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        val form = build(beginMillis = startMillis, isAllDay = true)
        assertThat(form.start.date).isEqualTo(LocalDate(2026, 6, 10))
        assertThat(form.end.date).isEqualTo(LocalDate(2026, 6, 10))
    }

    @Test
    fun `a leading RRULE prefix is stripped and a blank rule becomes null`() {
        assertThat(build(rrule = "RRULE:FREQ=WEEKLY").rrule).isEqualTo("FREQ=WEEKLY")
        assertThat(build(rrule = "   ").rrule).isNull()
        assertThat(build(rrule = null).rrule).isNull()
    }
}
