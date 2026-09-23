package de.jeanlucmakiola.calendula.ui.month

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Test

/**
 * Which chip a tap in a month cell lands on (#187) — the geometry the tap layer
 * uses to tell "open this event" from "open this day", given that the chips take
 * no pointer input of their own.
 */
class ChipAtCellYTest {

    private val zone = TimeZone.UTC
    private val jul26 = YearMonth(2026, Month.JULY)

    /** Band starts 40px down the cell; each lane is 20px tall. */
    private val bandTop = 40f
    private val laneHeight = 20f

    /** July 2026 starts on a Wednesday, so this row — Jul 6–12 — sits wholly inside it. */
    private fun rowOfJuly6(events: List<EventInstance>) =
        layoutMonthWeeks(jul26, DayOfWeek.MONDAY, events, zone)[1]

    private fun allDay(from: LocalDate, toInclusive: LocalDate, id: Long) = EventInstance(
        instanceId = id,
        eventId = id,
        calendarId = 1L,
        title = "A$id",
        start = from.atTime(0, 0).toInstant(zone),
        end = toInclusive.plus(1, DateTimeUnit.DAY).atTime(0, 0).toInstant(zone),
        isAllDay = true,
        color = 0xFF2196F3.toInt(),
        location = null,
    )

    private fun timed(date: LocalDate, hour: Int, id: Long) = EventInstance(
        instanceId = id,
        eventId = id,
        calendarId = 1L,
        title = "T$id",
        start = date.atTime(hour, 0).toInstant(zone),
        end = date.atTime(hour + 1, 0).toInstant(zone),
        isAllDay = false,
        color = 0xFFF44336.toInt(),
        location = null,
    )

    private fun MonthWeek.chipAt(col: Int, cellY: Float) =
        chipAtCellY(
            col = col,
            cellY = cellY,
            bandTopInCell = bandTop,
            rowHeightPx = laneHeight,
            laneCap = SPLIT_DOT_LANES,
        )

    @Test
    fun `a tap on a lane resolves to the chip seated there`() {
        val bar = allDay(LocalDate(2026, 7, 7), LocalDate(2026, 7, 9), id = 1L)
        val meeting = timed(LocalDate(2026, 7, 7), hour = 9, id = 2L)
        val week = rowOfJuly6(listOf(bar, meeting))

        // Jul 7 is column 1 of a Monday-anchored row starting Jul 6.
        assertThat(week.chipAt(col = 1, cellY = bandTop + 5f)?.eventId).isEqualTo(1L)
        assertThat(week.chipAt(col = 1, cellY = bandTop + laneHeight + 5f)?.eventId).isEqualTo(2L)
    }

    @Test
    fun `a multi-day bar answers on every column it covers`() {
        val bar = allDay(LocalDate(2026, 7, 7), LocalDate(2026, 7, 9), id = 1L)
        val week = rowOfJuly6(listOf(bar))

        (1..3).forEach { col ->
            assertThat(week.chipAt(col = col, cellY = bandTop + 5f)?.eventId).isEqualTo(1L)
        }
        // Jul 10 is past the bar's last day.
        assertThat(week.chipAt(col = 4, cellY = bandTop + 5f)).isNull()
    }

    @Test
    fun `a tap above the band is the day number, not a chip`() {
        val week = rowOfJuly6(listOf(timed(LocalDate(2026, 7, 7), hour = 9, id = 2L)))

        assertThat(week.chipAt(col = 1, cellY = bandTop - 1f)).isNull()
        assertThat(week.chipAt(col = 1, cellY = 0f)).isNull()
    }

    @Test
    fun `a tap on an empty lane of a day that has chips falls through to the day`() {
        val week = rowOfJuly6(listOf(timed(LocalDate(2026, 7, 7), hour = 9, id = 2L)))

        assertThat(week.chipAt(col = 1, cellY = bandTop + laneHeight * 2 + 5f)).isNull()
    }

    @Test
    fun `a tap on the overflow row opens the day rather than a hidden event`() {
        val events = (1..SPLIT_DOT_LANES + 2).map {
            timed(LocalDate(2026, 7, 7), hour = it, id = it.toLong())
        }
        val week = rowOfJuly6(events)

        // The dots sit one lane below the last one the row draws.
        val overflowY = bandTop + laneHeight * SPLIT_DOT_LANES + 2f
        assertThat(week.chipAt(col = 1, cellY = overflowY)).isNull()
    }

    @Test
    fun `unmeasured geometry resolves to no chip`() {
        val week = rowOfJuly6(listOf(timed(LocalDate(2026, 7, 7), hour = 9, id = 2L)))

        assertThat(
            week.chipAtCellY(
                col = 1,
                cellY = 45f,
                bandTopInCell = null,
                rowHeightPx = laneHeight,
                laneCap = SPLIT_DOT_LANES,
            ),
        ).isNull()
        assertThat(
            week.chipAtCellY(
                col = 1,
                cellY = 45f,
                bandTopInCell = bandTop,
                rowHeightPx = 0f,
                laneCap = SPLIT_DOT_LANES,
            ),
        ).isNull()
    }
}
