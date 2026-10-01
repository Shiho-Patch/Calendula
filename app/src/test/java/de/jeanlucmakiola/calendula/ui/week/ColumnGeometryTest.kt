package de.jeanlucmakiola.calendula.ui.week

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.time.Instant
import org.junit.jupiter.api.Test

/** The timeline's columns, standing still and mid view switch (#184). */
class ColumnGeometryTest {

    // 2026-09-27 is a Sunday; the week runs Sun..Sat.
    private val sunday = LocalDate(2026, 9, 27)
    private val week = DaySpan(sunday, 7)
    private val monday = sunday.plus(1, DateTimeUnit.DAY)
    private val day = DaySpan(monday, 1)
    private val width = 700f
    private val gap = 7f

    @Test
    fun `a page's columns follow the pitch`() {
        val slot = week.slot(sunday.plus(3, DateTimeUnit.DAY), width, gap)
        assertThat(slot.left).isEqualTo(3 * 101f)
        assertThat(slot.width).isEqualTo(94f)
    }

    @Test
    fun `the ends of a switch are the two pages`() {
        var p = 0f
        val geometry = ColumnGeometry(week, day) { p }
        assertThat(geometry.slot(monday, width, gap)).isEqualTo(week.slot(monday, width, gap))
        p = 1f
        assertThat(geometry.slot(monday, width, gap)).isEqualTo(day.slot(monday, width, gap))
    }

    @Test
    fun `days off the target page end off screen on their side`() {
        val geometry = ColumnGeometry(week, day) { 1f }
        assertThat(geometry.slot(sunday, width, gap).right).isAtMost(0f)
        assertThat(geometry.slot(monday.plus(1, DateTimeUnit.DAY), width, gap).left).isAtLeast(width)
    }

    @Test
    fun `a switch covers both pages without a break`() {
        // Focus on Saturday: the six-day run starting there reaches into next week.
        val saturday = sunday.plus(6, DateTimeUnit.DAY)
        val geometry = ColumnGeometry(week, DaySpan(saturday, 6)) { 0f }
        assertThat(geometry.dates).hasSize(12)
        assertThat(geometry.dates.first()).isEqualTo(sunday)
        assertThat(geometry.dates.zipWithNext().all { (a, b) -> b == a.plus(1, DateTimeUnit.DAY) }).isTrue()
    }

    @Test
    fun `a bar spans its extent on each page`() {
        val tuesday = monday.plus(1, DateTimeUnit.DAY)
        val range = monday..tuesday
        val geometry = ColumnGeometry(week, day) { 0f }
        val extent = geometry.extent(range, monday..monday, width, 0f)
        assertThat(extent.left).isEqualTo(week.slot(monday, width, 0f).left)
        assertThat(extent.right).isEqualTo(week.slot(tuesday, width, 0f).right)
    }

    @Test
    fun `a bar only one page has keeps its days on the other`() {
        val bar = AllDaySpan(event(1), startCol = 2, endCol = 3, lane = 1)
        val from = state(week, listOf(bar))
        val to = state(day, emptyList())
        val placed = morphBars(from, to).single()
        assertThat(placed.fromRange).isEqualTo(placed.toRange)
        assertThat(placed.toLane).isEqualTo(1)
    }

    @Test
    fun `a bar on both pages is placed once, clipped to each`() {
        val event = event(1)
        val from = state(week, listOf(AllDaySpan(event, startCol = 0, endCol = 6, lane = 0)))
        val to = state(day, listOf(AllDaySpan(event, startCol = 0, endCol = 0, lane = 0)))
        val placed = morphBars(from, to).single()
        assertThat(placed.fromRange).isEqualTo(sunday..sunday.plus(6, DateTimeUnit.DAY))
        assertThat(placed.toRange).isEqualTo(monday..monday)
    }

    private fun state(span: DaySpan, bars: List<AllDaySpan>): WeekUiState.Success {
        val days = (0 until span.count).map { span.start.plus(it, DateTimeUnit.DAY) }
        return WeekUiState.Success(
            weekStart = span.start,
            today = sunday,
            days = days,
            allDaySpans = bars,
            timedByDay = emptyMap(),
        )
    }

    private fun event(id: Long) = EventInstance(
        instanceId = id,
        eventId = id,
        calendarId = 1L,
        title = "Event",
        start = Instant.fromEpochMilliseconds(0),
        end = Instant.fromEpochMilliseconds(0),
        isAllDay = true,
        color = 0xFF000000.toInt(),
        location = null,
    )
}
