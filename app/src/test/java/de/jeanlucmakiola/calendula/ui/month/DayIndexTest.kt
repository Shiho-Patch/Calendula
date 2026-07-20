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
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Test

/**
 * [DayIndex] exists purely to make the scrolling styles cheap, so what it owes
 * is that cheapness changes *nothing*: a row laid out from the index must be the
 * row that would have come from scanning every instance.
 *
 * Run in a zone east of UTC, where the all-day anchoring and the day boundaries
 * disagree — the case that has bitten this codebase before (#65).
 */
class DayIndexTest {

    private val zone = TimeZone.of("Europe/Berlin")
    private val mon = LocalDate(2026, 6, 8)
    private val week = (0..6).map { mon.plus(it, DateTimeUnit.DAY) }

    private var nextId = 0L

    private fun timed(from: LocalDate, startHour: Int, to: LocalDate, endHour: Int): EventInstance {
        val id = ++nextId
        return EventInstance(
            instanceId = id,
            eventId = id,
            calendarId = 1L,
            title = "T$id",
            start = from.atTime(startHour, 0).toInstant(zone),
            end = to.atTime(endHour, 0).toInstant(zone),
            isAllDay = false,
            color = 0xFF112233.toInt(),
            location = null,
        )
    }

    private fun allDay(from: LocalDate, toInclusive: LocalDate): EventInstance {
        val id = ++nextId
        return EventInstance(
            instanceId = id,
            eventId = id,
            calendarId = 1L,
            title = "A$id",
            start = from.atTime(0, 0).toInstant(TimeZone.UTC),
            end = toInclusive.plus(1, DateTimeUnit.DAY).atTime(0, 0).toInstant(TimeZone.UTC),
            isAllDay = true,
            color = 0xFF445566.toInt(),
            location = null,
        )
    }

    /** Every shape the layout distinguishes, plus the boundary cases around them. */
    private fun awkwardEvents(): List<EventInstance> = listOf(
        timed(mon, 9, mon, 10),
        // Crosses midnight into the next day.
        timed(mon.plus(1, DateTimeUnit.DAY), 23, mon.plus(2, DateTimeUnit.DAY), 1),
        // Ends exactly at midnight: covers its own day, not the next.
        timed(mon.plus(3, DateTimeUnit.DAY), 22, mon.plus(4, DateTimeUnit.DAY), 0),
        // Zero length, at midnight: covers nothing at all.
        timed(mon.plus(4, DateTimeUnit.DAY), 0, mon.plus(4, DateTimeUnit.DAY), 0),
        allDay(mon.plus(2, DateTimeUnit.DAY), mon.plus(2, DateTimeUnit.DAY)),
        // Runs in from before the row and out past its end.
        allDay(mon.minus(3, DateTimeUnit.DAY), mon.plus(9, DateTimeUnit.DAY)),
        // Wholly outside the row, both sides.
        timed(mon.minus(10, DateTimeUnit.DAY), 9, mon.minus(10, DateTimeUnit.DAY), 10),
        allDay(mon.plus(20, DateTimeUnit.DAY), mon.plus(21, DateTimeUnit.DAY)),
        // Two on one day, to pin the ordering the index hands back.
        timed(mon.plus(5, DateTimeUnit.DAY), 8, mon.plus(5, DateTimeUnit.DAY), 9),
        timed(mon.plus(5, DateTimeUnit.DAY), 14, mon.plus(5, DateTimeUnit.DAY), 15),
    )

    @Test
    fun `a row from the index is the row from a full scan`() {
        val events = awkwardEvents()
        val scanned = layoutCalendarWeek(week, events, zone)
        val indexed = layoutCalendarWeek(week, DayIndex(events, zone).eventsOn(week), zone)
        assertThat(indexed).isEqualTo(scanned)
    }

    @Test
    fun `a whole month agrees, row for row`() {
        val events = awkwardEvents()
        val jun = YearMonth(2026, Month.JUNE)
        DayOfWeek.entries.forEach { ws ->
            assertThat(layoutMonthWeeks(jun, ws, DayIndex(events, zone), zone))
                .isEqualTo(layoutMonthWeeks(jun, ws, events, zone))
        }
    }

    @Test
    fun `an event covering several of a row's days is listed once`() {
        // It is bucketed on each date it covers, so the row would otherwise see
        // it repeatedly and lay out a lane per copy.
        val long = allDay(mon, mon.plus(4, DateTimeUnit.DAY))
        val events = DayIndex(listOf(long), zone).eventsOn(week)
        assertThat(events).containsExactly(long)
    }

    @Test
    fun `days no event touches come back empty`() {
        val index = DayIndex(listOf(timed(mon, 9, mon, 10)), zone)
        assertThat(index.eventsOn(listOf(mon.plus(1, DateTimeUnit.DAY)))).isEmpty()
        assertThat(index.eventsOn(emptyList())).isEmpty()
    }

    @Test
    fun `an empty calendar indexes to nothing`() {
        assertThat(DayIndex(emptyList(), zone).eventsOn(week)).isEmpty()
    }
}
