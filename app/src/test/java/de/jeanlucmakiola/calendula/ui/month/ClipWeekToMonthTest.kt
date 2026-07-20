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
 * What makes the continuous style's month blocks self-contained: a boundary week
 * keeps its seven columns but carries only the block's own month.
 */
class ClipWeekToMonthTest {

    private val zone = TimeZone.UTC
    private val jul26 = YearMonth(2026, Month.JULY)

    private fun allDay(from: LocalDate, toInclusive: LocalDate, id: Long = 1L) = EventInstance(
        instanceId = id,
        eventId = id,
        calendarId = 1L,
        title = "A",
        start = from.atTime(0, 0).toInstant(TimeZone.UTC),
        end = toInclusive.plus(1, DateTimeUnit.DAY).atTime(0, 0).toInstant(TimeZone.UTC),
        isAllDay = true,
        color = 0xFF445566.toInt(),
        location = null,
    )

    private fun timed(date: LocalDate, hour: Int, id: Long = 2L) = EventInstance(
        instanceId = id,
        eventId = id,
        calendarId = 1L,
        title = "T",
        start = date.atTime(hour, 0).toInstant(zone),
        end = date.atTime(hour + 1, 0).toInstant(zone),
        isAllDay = false,
        color = 0xFF112233.toInt(),
        location = null,
    )

    /** July 2026 starts on a Wednesday, so its first Monday-anchored row is Jun 29 – Jul 5. */
    private fun firstRowOfJuly(events: List<EventInstance>) =
        clipWeekToMonth(
            layoutMonthWeeks(jul26, DayOfWeek.MONDAY, events, zone).first(),
            jul26,
        )

    @Test
    fun `the row keeps all seven days so the columns don't shift`() {
        val week = firstRowOfJuly(emptyList())
        assertThat(week.days).hasSize(7)
        assertThat(week.days.first()).isEqualTo(LocalDate(2026, 6, 29))
    }

    @Test
    fun `the neighbour month's events are dropped`() {
        val june = timed(LocalDate(2026, 6, 30), 9)
        val july = timed(LocalDate(2026, 7, 1), 9, id = 3L)
        val week = firstRowOfJuly(listOf(june, july))

        assertThat(week.timedByDay.keys).doesNotContain(LocalDate(2026, 6, 30))
        assertThat(week.timedByDay[LocalDate(2026, 7, 1)]).containsExactly(july)
        assertThat(week.countByDay[LocalDate(2026, 6, 30)]).isNull()
        assertThat(week.countByDay[LocalDate(2026, 7, 1)]).isEqualTo(1)
    }

    @Test
    fun `a bar reaching in from the previous month is cut back to the 1st`() {
        // Jun 29 – Jul 2: columns 0..3 unclipped, 2..3 once July owns the row.
        val week = firstRowOfJuly(listOf(allDay(LocalDate(2026, 6, 29), LocalDate(2026, 7, 2))))

        val span = week.spans.single()
        assertThat(span.startCol).isEqualTo(2) // Wednesday the 1st
        assertThat(span.endCol).isEqualTo(3)
        // Flat cap on the cut side: the event continues out of this block.
        assertThat(span.continuesLeft).isTrue()
        assertThat(span.continuesRight).isFalse()
    }

    @Test
    fun `a bar living entirely in the neighbour month disappears`() {
        val week = firstRowOfJuly(listOf(allDay(LocalDate(2026, 6, 29), LocalDate(2026, 6, 30))))
        assertThat(week.spans).isEmpty()
    }

    @Test
    fun `a bar reaching out into the next month is cut at the last day`() {
        // Jul 29 – Aug 2 sits in July's final row (Jul 27 – Aug 2).
        val weeks = layoutMonthWeeks(
            jul26,
            DayOfWeek.MONDAY,
            listOf(allDay(LocalDate(2026, 7, 29), LocalDate(2026, 8, 2))),
            zone,
        )
        val span = clipWeekToMonth(weeks.last(), jul26).spans.single()
        assertThat(span.startCol).isEqualTo(2) // Wednesday the 29th
        assertThat(span.endCol).isEqualTo(4) // Friday the 31st
        assertThat(span.continuesRight).isTrue()
        assertThat(span.continuesLeft).isFalse()
    }

    @Test
    fun `a row wholly inside the month is untouched`() {
        val mid = layoutMonthWeeks(
            jul26,
            DayOfWeek.MONDAY,
            listOf(timed(LocalDate(2026, 7, 8), 9), allDay(LocalDate(2026, 7, 7), LocalDate(2026, 7, 9))),
            zone,
        )[1]
        assertThat(clipWeekToMonth(mid, jul26)).isEqualTo(mid)
    }
}
