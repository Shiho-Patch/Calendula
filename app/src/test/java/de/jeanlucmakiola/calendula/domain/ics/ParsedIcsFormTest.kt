package de.jeanlucmakiola.calendula.domain.ics

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Test

class ParsedIcsFormTest {

    private val berlin = TimeZone.of("Europe/Berlin")

    @Test
    fun `timed event maps to wall-clock form times in the device zone`() {
        val event = ParsedIcsEvent(
            uid = "u@x",
            summary = "Call",
            start = LocalDateTime(2026, 6, 18, 13, 0, 0).toInstant(TimeZone.UTC),
            end = LocalDateTime(2026, 6, 18, 14, 0, 0).toInstant(TimeZone.UTC),
            isAllDay = false,
            zoneId = "UTC",
            reminderMinutes = listOf(10, 10, 5),
            recurrenceRule = "FREQ=WEEKLY",
        )
        val form = event.toEventForm(berlin)

        assertThat(form.calendarId).isNull()
        assertThat(form.title).isEqualTo("Call")
        // 13:00 UTC == 15:00 Berlin (summer).
        assertThat(form.start).isEqualTo(LocalDateTime(2026, 6, 18, 15, 0, 0))
        assertThat(form.end).isEqualTo(LocalDateTime(2026, 6, 18, 16, 0, 0))
        assertThat(form.reminders).containsExactly(5, 10).inOrder()
        assertThat(form.rrule).isEqualTo("FREQ=WEEKLY")
    }

    @Test
    fun `all-day event shows the last covered day, not the exclusive end`() {
        val event = ParsedIcsEvent(
            uid = null,
            summary = "Trip",
            start = LocalDate(2026, 6, 18).atStartOfDayIn(TimeZone.UTC),
            end = LocalDate(2026, 6, 20).atStartOfDayIn(TimeZone.UTC), // exclusive
            isAllDay = true,
            zoneId = "UTC",
        )
        val form = event.toEventForm(berlin)

        assertThat(form.isAllDay).isTrue()
        assertThat(form.start.date).isEqualTo(LocalDate(2026, 6, 18))
        assertThat(form.end.date).isEqualTo(LocalDate(2026, 6, 19)) // last covered day
    }
}
