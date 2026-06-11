package de.jeanlucmakiola.calendula.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.junit.jupiter.api.Test

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
}
