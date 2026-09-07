package de.jeanlucmakiola.calendula.ui.month

import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.EventResponse
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Instant
import org.junit.jupiter.api.Test

class MonthLayoutTest {

    private val zone = TimeZone.UTC

    // 2026-06-01 is a Monday, so a Monday-anchored June grid starts on the 1st.
    private val jun = YearMonth(2026, kotlinx.datetime.Month.JUNE)
    private val jun1 = LocalDate(2026, 6, 1)
    private val jun8 = LocalDate(2026, 6, 8)
    private val weekOf8th = (0..6).map { jun8.plus(it, DateTimeUnit.DAY) }

    private fun at(date: LocalDate, h: Int, m: Int = 0): Instant =
        date.atTime(h, m).toInstant(zone)

    private fun timed(
        date: LocalDate,
        startHour: Int,
        endHour: Int,
        id: Long = 1L,
        title: String = "E",
    ) = EventInstance(
        instanceId = id,
        eventId = id,
        calendarId = 1L,
        title = title,
        start = at(date, startHour),
        end = at(date, endHour),
        isAllDay = false,
        color = 0xFF112233.toInt(),
        location = null,
    )

    /** A timed event running from [date] 20:00 into the next day at [endHour]. */
    private fun overnight(date: LocalDate, endHour: Int = 8) = EventInstance(
        instanceId = 7L,
        eventId = 7L,
        calendarId = 1L,
        title = "Night shift",
        start = at(date, 20),
        end = at(date.plus(1, DateTimeUnit.DAY), endHour),
        isAllDay = false,
        color = 0xFF112233.toInt(),
        location = null,
    )

    /** All-day events live at UTC midnights with an *exclusive* end. */
    private fun allDay(
        from: LocalDate,
        toInclusive: LocalDate = from,
        id: Long = 100L,
        title: String = "A",
    ) = EventInstance(
        instanceId = id,
        eventId = id,
        calendarId = 1L,
        title = title,
        start = from.atTime(0, 0).toInstant(TimeZone.UTC),
        end = toInclusive.plus(1, DateTimeUnit.DAY).atTime(0, 0).toInstant(TimeZone.UTC),
        isAllDay = true,
        color = 0xFF445566.toInt(),
        location = null,
    )

    @Test
    fun `startOfGridWeek snaps back to the configured week start`() {
        val wed = LocalDate(2026, 6, 10)
        assertThat(wed.startOfGridWeek(DayOfWeek.MONDAY)).isEqualTo(jun8)
        assertThat(jun8.startOfGridWeek(DayOfWeek.MONDAY)).isEqualTo(jun8)
        // A Sunday-anchored week containing the 10th starts on the 7th.
        assertThat(wed.startOfGridWeek(DayOfWeek.SUNDAY)).isEqualTo(LocalDate(2026, 6, 7))
    }

    @Test
    fun `monthGridRange always covers 42 days from the grid start`() {
        val range = monthGridRange(jun, DayOfWeek.MONDAY, zone)
        assertThat(range.start).isEqualTo(at(jun1, 0))
        // 42 days inclusive → the last second of 2026-07-12.
        assertThat(range.endInclusive)
            .isEqualTo(LocalDate(2026, 7, 12).atTime(23, 59, 59).toInstant(zone))
    }

    @Test
    fun `week count follows the month's shape rather than a fixed six rows`() {
        // June 2026: starts Monday, 30 days → 5 rows.
        assertThat(layoutMonthWeeks(jun, DayOfWeek.MONDAY, emptyList(), zone)).hasSize(5)
        // August 2026: starts Saturday, 31 days → spills to 6 rows.
        val aug = YearMonth(2026, kotlinx.datetime.Month.AUGUST)
        assertThat(layoutMonthWeeks(aug, DayOfWeek.MONDAY, emptyList(), zone)).hasSize(6)
        // February 2021: starts Monday, 28 days → exactly 4 rows.
        val feb = YearMonth(2021, kotlinx.datetime.Month.FEBRUARY)
        assertThat(layoutMonthWeeks(feb, DayOfWeek.MONDAY, emptyList(), zone)).hasSize(4)
    }

    @Test
    fun `layoutMonthWeeks rows are contiguous and seven days wide`() {
        val weeks = layoutMonthWeeks(jun, DayOfWeek.MONDAY, emptyList(), zone)
        assertThat(weeks.first().days.first()).isEqualTo(jun1)
        weeks.forEach { assertThat(it.days).hasSize(7) }
        val allDays = weeks.flatMap { it.days }
        allDays.zipWithNext { a, b -> assertThat(b).isEqualTo(a.plus(1, DateTimeUnit.DAY)) }
    }

    @Test
    fun `a multi-day event becomes one span across its columns`() {
        val ev = allDay(LocalDate(2026, 6, 10), LocalDate(2026, 6, 12))
        val week = layoutCalendarWeek(weekOf8th, listOf(ev), zone)

        assertThat(week.spans).hasSize(1)
        val span = week.spans.single()
        assertThat(span.startCol).isEqualTo(2) // Wednesday the 10th
        assertThat(span.endCol).isEqualTo(4) // Friday the 12th
        assertThat(span.lane).isEqualTo(0)
        assertThat(span.continuesLeft).isFalse()
        assertThat(span.continuesRight).isFalse()
    }

    @Test
    fun `a span running past the row end is flagged as continuing`() {
        // Saturday the 13th through Tuesday the 16th straddles the row boundary.
        val ev = allDay(LocalDate(2026, 6, 13), LocalDate(2026, 6, 16))
        val week = layoutCalendarWeek(weekOf8th, listOf(ev), zone)

        val span = week.spans.single()
        assertThat(span.startCol).isEqualTo(5)
        assertThat(span.endCol).isEqualTo(6)
        assertThat(span.continuesLeft).isFalse()
        assertThat(span.continuesRight).isTrue()

        // The following row picks it up with the flags mirrored.
        val nextRow = layoutCalendarWeek(
            weekOf8th.map { it.plus(7, DateTimeUnit.DAY) },
            listOf(ev),
            zone,
        )
        val tail = nextRow.spans.single()
        assertThat(tail.startCol).isEqualTo(0)
        assertThat(tail.endCol).isEqualTo(1)
        assertThat(tail.continuesLeft).isTrue()
        assertThat(tail.continuesRight).isFalse()
    }

    @Test
    fun `overlapping spans are stacked on separate lanes`() {
        val a = allDay(LocalDate(2026, 6, 9), LocalDate(2026, 6, 11), id = 1L)
        val b = allDay(LocalDate(2026, 6, 10), LocalDate(2026, 6, 12), id = 2L)
        val week = layoutCalendarWeek(weekOf8th, listOf(a, b), zone)

        assertThat(week.spans.map { it.lane }).containsExactly(0, 1)
    }

    @Test
    fun `single-day timed events stay pills and sort by start`() {
        val late = timed(LocalDate(2026, 6, 10), 14, 15, id = 1L)
        val early = timed(LocalDate(2026, 6, 10), 9, 10, id = 2L)
        val week = layoutCalendarWeek(weekOf8th, listOf(late, early), zone)

        assertThat(week.spans).isEmpty()
        assertThat(week.timedByDay[LocalDate(2026, 6, 10)]?.map { it.instanceId })
            .containsExactly(2L, 1L)
            .inOrder()
        assertThat(week.timedByDay[LocalDate(2026, 6, 11)]).isEmpty()
    }

    @Test
    fun `an event crossing midnight is one bar, not two pills, on both sides of a week seam`() {
        // Sun 14 Jun 20:00 to Mon 15 Jun 08:00 — the seam of a Monday-anchored
        // grid: one day in this row, one in the next. Counting per row made each
        // a standalone pill with nothing saying they were one event (#253).
        val overnight = overnight(LocalDate(2026, 6, 14))
        val head = layoutCalendarWeek(weekOf8th, listOf(overnight), zone)
        assertThat(head.timedByDay.values.flatten()).isEmpty()
        val headSpan = head.spans.single()
        assertThat(headSpan.startCol).isEqualTo(6)
        assertThat(headSpan.endCol).isEqualTo(6)
        assertThat(headSpan.continuesLeft).isFalse()
        assertThat(headSpan.continuesRight).isTrue()

        val tailRow = layoutCalendarWeek(
            weekOf8th.map { it.plus(7, DateTimeUnit.DAY) },
            listOf(overnight),
            zone,
        )
        assertThat(tailRow.timedByDay.values.flatten()).isEmpty()
        val tailSpan = tailRow.spans.single()
        assertThat(tailSpan.startCol).isEqualTo(0)
        assertThat(tailSpan.endCol).isEqualTo(0)
        assertThat(tailSpan.continuesLeft).isTrue()
        assertThat(tailSpan.continuesRight).isFalse()
    }

    @Test
    fun `an event ending exactly at midnight stays a single-day pill`() {
        val untilMidnight = overnight(LocalDate(2026, 6, 10), endHour = 0)
        val week = layoutCalendarWeek(weekOf8th, listOf(untilMidnight), zone)

        assertThat(week.spans).isEmpty()
        assertThat(week.timedByDay[LocalDate(2026, 6, 10)]).hasSize(1)
        assertThat(week.timedByDay[LocalDate(2026, 6, 11)]).isEmpty()
    }

    @Test
    fun `countByDay totals bars and pills on each date`() {
        val span = allDay(LocalDate(2026, 6, 10), LocalDate(2026, 6, 12), id = 1L)
        val pill = timed(LocalDate(2026, 6, 10), 9, 10, id = 2L)
        val week = layoutCalendarWeek(weekOf8th, listOf(span, pill), zone)

        assertThat(week.countByDay[LocalDate(2026, 6, 10)]).isEqualTo(2)
        assertThat(week.countByDay[LocalDate(2026, 6, 11)]).isEqualTo(1)
        assertThat(week.countByDay[LocalDate(2026, 6, 13)]).isEqualTo(0)
    }

    @Test
    fun `layoutMonthWeeks agrees with laying each row out on its own`() {
        val events = listOf(
            allDay(LocalDate(2026, 6, 10), LocalDate(2026, 6, 12), id = 1L),
            timed(LocalDate(2026, 6, 18), 9, 10, id = 2L),
        )
        val weeks = layoutMonthWeeks(jun, DayOfWeek.MONDAY, events, zone)

        weeks.forEach { row ->
            assertThat(row).isEqualTo(layoutCalendarWeek(row.days, events, zone))
        }
    }

    @Test
    fun `instancesByDay puts all-day events first then orders by start`() {
        val allDayEv = allDay(LocalDate(2026, 6, 10), id = 1L)
        val late = timed(LocalDate(2026, 6, 10), 14, 15, id = 2L)
        val early = timed(LocalDate(2026, 6, 10), 9, 10, id = 3L)

        val byDay = instancesByDay(weekOf8th, listOf(late, early, allDayEv), zone)

        assertThat(byDay.getValue(LocalDate(2026, 6, 10)).map { it.instanceId })
            .containsExactly(1L, 3L, 2L)
            .inOrder()
    }

    @Test
    fun `instancesByDay repeats a multi-day event on every date it covers`() {
        val ev = allDay(LocalDate(2026, 6, 10), LocalDate(2026, 6, 12))
        val byDay = instancesByDay(weekOf8th, listOf(ev), zone)

        assertThat(byDay.getValue(LocalDate(2026, 6, 9))).isEmpty()
        assertThat(byDay.getValue(LocalDate(2026, 6, 10))).hasSize(1)
        assertThat(byDay.getValue(LocalDate(2026, 6, 11))).hasSize(1)
        assertThat(byDay.getValue(LocalDate(2026, 6, 12))).hasSize(1)
        assertThat(byDay.getValue(LocalDate(2026, 6, 13))).isEmpty()
    }

    @Test
    fun `instancesByDay covers every date in the grid, empty ones included`() {
        val byDay = instancesByDay(weekOf8th, emptyList(), zone)
        assertThat(byDay.keys).containsExactlyElementsIn(weekOf8th)
        assertThat(byDay.values.flatten()).isEmpty()
    }

    @Test
    fun `a declined event sorts behind every answered one on its day`() {
        val day = LocalDate(2026, 6, 10)
        // Earliest of the three, and all-day on top of that, so only the
        // declined ordering can put it last.
        val declined = allDay(day, id = 1L, title = "Declined")
            .copy(response = EventResponse.Declined)
        val morning = timed(day, 9, 10, id = 2L, title = "Morning")
        val evening = timed(day, 18, 19, id = 3L, title = "Evening")
        val events = listOf(declined, morning, evening)

        assertThat(instancesByDay(listOf(day), events, zone).getValue(day).map { it.title })
            .containsExactly("Morning", "Evening", "Declined").inOrder()

        val timedOnly =
            layoutCalendarWeek(weekOf8th, listOf(evening, morning, declinedTimed(day)), zone)
        assertThat(timedOnly.timedByDay.getValue(day).map { it.title })
            .containsExactly("Morning", "Evening", "Declined early").inOrder()
    }

    @Test
    fun `a declined bar packed last still shares a lane it does not overlap`() {
        // Sorting declined last means this bar is seated after one that reaches
        // further right than it starts. A lane must still be offered on the
        // columns it is actually free on, or the row grows a wasted rank.
        val mon = weekOf8th[0]
        val declined = allDay(mon, id = 1L, title = "Declined")
            .copy(response = EventResponse.Declined)
        val going = allDay(weekOf8th[2], weekOf8th[3], id = 2L, title = "Going")
        val week = layoutCalendarWeek(weekOf8th, listOf(declined, going), zone)

        assertThat(week.spans.map { it.lane }).containsExactly(0, 0)
    }

    @Test
    fun `a declined all-day bar takes a lane below the ones you answered`() {
        val day = LocalDate(2026, 6, 10)
        val declined =
            allDay(day, id = 1L, title = "Declined").copy(response = EventResponse.Declined)
        val going = allDay(day, id = 2L, title = "Going")
        // Declined listed first: only the sort, not the input order, may decide.
        val week = layoutCalendarWeek(weekOf8th, listOf(declined, going), zone)

        val lanes = week.spans.associate { it.event.title to it.lane }
        assertThat(lanes.getValue("Going")).isLessThan(lanes.getValue("Declined"))
    }

    @Test
    fun `overlapping bars never share a lane, whatever order they arrive in`() {
        val a = allDay(weekOf8th[0], weekOf8th[3], id = 1L, title = "A")
        val b = allDay(weekOf8th[2], weekOf8th[5], id = 2L, title = "B")
        val c = allDay(weekOf8th[3], weekOf8th[4], id = 3L, title = "C")
            .copy(response = EventResponse.Declined)
        val lanes = layoutCalendarWeek(weekOf8th, listOf(c, b, a), zone)
            .spans.associate { it.event.title to it.lane }

        assertThat(lanes.getValue("A")).isNotEqualTo(lanes.getValue("B"))
        assertThat(lanes.getValue("B")).isNotEqualTo(lanes.getValue("C"))
        assertThat(lanes.getValue("A")).isNotEqualTo(lanes.getValue("C"))
    }

    /** A declined timed event starting before every other one in its test. */
    private fun declinedTimed(date: LocalDate) =
        timed(date, 7, 8, id = 4L, title = "Declined early")
            .copy(response = EventResponse.Declined)
}
