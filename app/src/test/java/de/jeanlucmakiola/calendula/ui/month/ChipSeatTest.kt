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

    /** A grid holding [seated] in lane 0 across [cols], and nothing else. */
    private fun row(seated: EventInstance, cols: IntRange): (Int, Int) -> EventInstance? =
        { col, lane -> seated.takeIf { lane == 0 && col in cols } }

    @Test
    fun `a single-day chip is seated in its own column`() {
        val ev = event(1L)
        assertThat(chipSeat(days, 3, row(ev, 3..3), ev, days[3])).isEqualTo(3 to 0)
    }

    @Test
    fun `a bar is seated from its first column, not the day it was dropped on`() {
        val ev = event(1L)
        // Covers Wed–Thu; dropped on the Thursday it now runs into.
        assertThat(chipSeat(days, 3, row(ev, 2..3), ev, days[3])).isEqualTo(2 to 0)
    }

    @Test
    fun `a bar carried in from the previous week is seated at column zero`() {
        val ev = event(1L)
        assertThat(chipSeat(days, 3, row(ev, 0..4), ev, days[4])).isEqualTo(0 to 0)
    }

    @Test
    fun `a row that does not hold the event seats nothing`() {
        val ev = event(1L)
        assertThat(chipSeat(days, 3, row(ev, 2..3), ev, days[5])).isNull()
        assertThat(chipSeat(days, 3, row(ev, 2..3), ev, LocalDate(2026, 7, 1))).isNull()
    }

    @Test
    fun `a re-read chip is matched by title once its event id has changed`() {
        // A single-occurrence move writes a new event id; the title survives.
        val moved = event(1L, title = "Standup")
        val reRead = event(9L, title = "Standup")
        assertThat(chipSeat(days, 3, row(reRead, 3..3), moved, days[3])).isEqualTo(3 to 0)
    }

    @Test
    fun `two untitled events are not the same event`() {
        val moved = event(1L, title = "")
        val other = event(2L, title = "")
        assertThat(isSameEvent(other, moved)).isFalse()
        assertThat(chipSeat(days, 3, row(other, 3..3), moved, days[3])).isNull()
    }
}
