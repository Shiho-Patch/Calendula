package de.jeanlucmakiola.calendula.ui.month

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Test

/**
 * Where a week row draws a moved chip — what the settling copy glides onto
 * (#68, #253).
 */
class ChipSeatTest {

    private val monday = LocalDate(2026, 6, 8)
    private val days = (0..6).map { monday.plus(it, DateTimeUnit.DAY) }

    private fun event(id: Long, title: String = "E") = EventInstance(
        instanceId = id,
        eventId = id,
        calendarId = 1L,
        title = title,
        start = monday.atTime(9, 0).toInstant(TimeZone.UTC),
        end = monday.atTime(10, 0).toInstant(TimeZone.UTC),
        isAllDay = false,
        color = 0xFF112233.toInt(),
        location = null,
    )

    /**
     * A grid holding [seated] in lane 0 as one bar across [cols], and nothing
     * else — the seating a [MonthWeek] gives a span.
     */
    private fun row(seated: EventInstance, cols: IntRange) = MonthWeek(
        days = days,
        spans = listOf(
            MonthSpan(
                event = seated,
                startCol = cols.first,
                endCol = cols.last,
                lane = 0,
                continuesLeft = false,
                continuesRight = false,
            ),
        ),
        timedByDay = emptyMap(),
        countByDay = emptyMap(),
    )

    /** A grid holding one single-day pill per day of [cols], all of [seated]. */
    private fun pills(seated: EventInstance, cols: IntRange) = MonthWeek(
        days = days,
        spans = emptyList(),
        timedByDay = cols.associate { days[it] to listOf(seated) },
        countByDay = emptyMap(),
    )

    private fun seat(week: MonthWeek, event: EventInstance, date: LocalDate) = chipSeat(
        days = days,
        laneCount = 3,
        chipAt = { col, lane -> week.chipAt(col, lane, 3) },
        chipStart = { col, lane -> week.chipStartCol(col, lane) },
        event = event,
        date = date,
    )

    @Test
    fun `a single-day chip is seated in its own column`() {
        val ev = event(1L)
        assertThat(seat(row(ev, 3..3), ev, days[3])).isEqualTo(3 to 0)
    }

    @Test
    fun `a bar is seated from its first column, not the day it was dropped on`() {
        val ev = event(1L)
        // Covers Wed–Thu; dropped on the Thursday it now runs into.
        assertThat(seat(row(ev, 2..3), ev, days[3])).isEqualTo(2 to 0)
    }

    @Test
    fun `a bar carried in from the previous week is seated at column zero`() {
        val ev = event(1L)
        assertThat(seat(row(ev, 0..4), ev, days[4])).isEqualTo(0 to 0)
    }

    @Test
    fun `a row that does not hold the event seats nothing`() {
        val ev = event(1L)
        assertThat(seat(row(ev, 2..3), ev, days[5])).isNull()
        assertThat(seat(row(ev, 2..3), ev, LocalDate(2026, 7, 1))).isNull()
    }

    @Test
    fun `a re-read chip is matched by title once its event id has changed`() {
        // A single-occurrence move writes a new event id; the title survives.
        val moved = event(1L, title = "Standup")
        val reRead = event(9L, title = "Standup")
        assertThat(seat(row(reRead, 3..3), moved, days[3])).isEqualTo(3 to 0)
    }

    @Test
    fun `two untitled events are not the same event`() {
        val moved = event(1L, title = "")
        val other = event(2L, title = "")
        assertThat(isSameEvent(other, moved)).isFalse()
        assertThat(seat(row(other, 3..3), moved, days[3])).isNull()
    }

    @Test
    fun `a daily series seats each occurrence in its own column`() {
        // Every column holds an occurrence of the same event id, so a walk left
        // by identity would run to the start of the row (#253).
        val ev = event(1L, title = "Standup")
        assertThat(seat(pills(ev, 0..6), ev, days[4])).isEqualTo(4 to 0)
    }
}
