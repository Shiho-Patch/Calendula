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
 * What lets the split style's dots morph into the paged style's bars (#53): both
 * read a day off the *same* lane seating, so dot _i_ and lane _i_ are one event.
 */
class LaneEventsTest {

    private val zone = TimeZone.UTC
    private val jul26 = YearMonth(2026, Month.JULY)

    /** July 2026 starts on a Wednesday, so this row — Jul 6–12 — sits wholly inside it. */
    private fun rowOfJuly6(events: List<EventInstance>) =
        layoutMonthWeeks(jul26, DayOfWeek.MONDAY, events, zone)[1]

    private fun allDay(from: LocalDate, toInclusive: LocalDate, id: Long, color: Int = BLUE) =
        EventInstance(
            instanceId = id,
            eventId = id,
            calendarId = 1L,
            title = "A$id",
            start = from.atTime(0, 0).toInstant(zone),
            end = toInclusive.plus(1, DateTimeUnit.DAY).atTime(0, 0).toInstant(zone),
            isAllDay = true,
            color = color,
            location = null,
        )

    private fun timed(date: LocalDate, hour: Int, id: Long, color: Int = RED) = EventInstance(
        instanceId = id,
        eventId = id,
        calendarId = 1L,
        title = "T$id",
        start = date.atTime(hour, 0).toInstant(zone),
        end = date.atTime(hour + 1, 0).toInstant(zone),
        isAllDay = false,
        color = color,
        location = null,
    )

    @Test
    fun `a bar keeps its lane and the day's timed events fill what's left`() {
        val bar = allDay(LocalDate(2026, 7, 7), LocalDate(2026, 7, 9), id = 1L)
        val meeting = timed(LocalDate(2026, 7, 7), hour = 9, id = 2L)
        val week = rowOfJuly6(listOf(bar, meeting))

        // Jul 7 is column 1 of a Monday-anchored row starting Jul 6.
        assertThat(week.laneEvents(col = 1, day = LocalDate(2026, 7, 7), laneCap = 3))
            .containsExactly(bar, meeting)
            .inOrder()
    }

    @Test
    fun `a day the bar misses seats its own events from lane zero`() {
        val bar = allDay(LocalDate(2026, 7, 7), LocalDate(2026, 7, 9), id = 1L)
        val monday = timed(LocalDate(2026, 7, 6), hour = 9, id = 2L)
        val week = rowOfJuly6(listOf(bar, monday))

        assertThat(week.laneEvents(col = 0, day = LocalDate(2026, 7, 6), laneCap = 3))
            .containsExactly(monday)
    }

    @Test
    fun `a multi-day bar is seated on every day it covers`() {
        val bar = allDay(LocalDate(2026, 7, 7), LocalDate(2026, 7, 9), id = 1L)
        val week = rowOfJuly6(listOf(bar))

        (1..3).forEach { col ->
            val day = LocalDate(2026, 7, 6 + col)
            assertThat(week.laneEvents(col, day, laneCap = 3)).containsExactly(bar)
        }
        assertThat(week.laneEvents(col = 4, day = LocalDate(2026, 7, 10), laneCap = 3)).isEmpty()
    }

    /** The bug the colour-gathered dots had: one dot for two events on one calendar. */
    @Test
    fun `events sharing a colour each keep their own lane`() {
        val first = timed(LocalDate(2026, 7, 6), hour = 9, id = 1L, color = RED)
        val second = timed(LocalDate(2026, 7, 6), hour = 14, id = 2L, color = RED)
        val week = rowOfJuly6(listOf(first, second))

        assertThat(week.laneEvents(col = 0, day = LocalDate(2026, 7, 6), laneCap = 3))
            .containsExactly(first, second)
            .inOrder()
    }

    @Test
    fun `seating stops at the cap and leaves the rest to the overflow count`() {
        val events = (0 until 5).map { timed(LocalDate(2026, 7, 6), hour = 8 + it, id = it + 1L) }
        val week = rowOfJuly6(events)

        val seated = week.laneEvents(col = 0, day = LocalDate(2026, 7, 6), laneCap = 3)
        assertThat(seated).hasSize(3)
        assertThat(seated).containsExactlyElementsIn(events.take(3)).inOrder()
        assertThat(week.countByDay[LocalDate(2026, 7, 6)]!! - seated.size).isEqualTo(2)
    }

    /** A bar parked below the cap is out of view, so it takes no dot with it. */
    @Test
    fun `a bar beyond the cap is left out`() {
        val bars = (0 until 4).map {
            allDay(LocalDate(2026, 7, 6), LocalDate(2026, 7, 8), id = it + 1L)
        }
        val week = rowOfJuly6(bars)

        val seated = week.laneEvents(col = 0, day = LocalDate(2026, 7, 6), laneCap = 3)
        assertThat(seated).hasSize(3)
        assertThat(week.spans.filter { it.lane >= 3 }.map { it.event })
            .containsNoneIn(seated)
    }

    @Test
    fun `overflow is exactly what seating left behind`() {
        val events = (0 until 5).map { timed(LocalDate(2026, 7, 6), hour = 8 + it, id = it + 1L) }
        val week = rowOfJuly6(events)
        val day = LocalDate(2026, 7, 6)

        val seated = week.laneEvents(col = 0, day = day, laneCap = 3)
        val overflow = week.overflowEvents(col = 0, day = day, laneCap = 3)

        assertThat(overflow).containsExactlyElementsIn(events.drop(3)).inOrder()
        assertThat(seated + overflow).containsExactlyElementsIn(events)
        assertThat(seated.size + overflow.size).isEqualTo(week.countByDay[day])
    }

    @Test
    fun `a bar beyond the cap overflows on every day it covers`() {
        val bars = (0 until 4).map {
            allDay(LocalDate(2026, 7, 6), LocalDate(2026, 7, 8), id = it + 1L)
        }
        val week = rowOfJuly6(bars)
        val parked = week.spans.filter { it.lane >= 3 }.map { it.event }

        (0..2).forEach { col ->
            val day = LocalDate(2026, 7, 6 + col)
            assertThat(week.overflowEvents(col, day, laneCap = 3))
                .containsExactlyElementsIn(parked)
        }
    }

    @Test
    fun `a day that fits has no overflow`() {
        val week = rowOfJuly6(listOf(timed(LocalDate(2026, 7, 6), hour = 9, id = 1L)))

        assertThat(week.overflowEvents(col = 0, day = LocalDate(2026, 7, 6), laneCap = 3)).isEmpty()
    }

    private companion object {
        const val BLUE = 0xFF3366CC.toInt()
        const val RED = 0xFFCC3333.toInt()
    }
}
