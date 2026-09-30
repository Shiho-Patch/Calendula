package de.jeanlucmakiola.calendula.ui.month

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.jupiter.api.Test

/**
 * The continuous grid's coordinate space. Every block's identity — and the
 * LazyColumn items it maps to — hangs off this arithmetic, so it gets its own
 * tests rather than being exercised only through the UI.
 */
class ContinuousMonthIndexTest {

    private val jun26 = YearMonth(2026, Month.JUNE)

    @Test
    fun `consecutive months are consecutive indices`() {
        val jun = monthIndexOf(jun26)
        assertThat(monthIndexOf(YearMonth(2026, Month.JULY))).isEqualTo(jun + 1)
        assertThat(monthIndexOf(YearMonth(2026, Month.MAY))).isEqualTo(jun - 1)
        // And across the year boundary.
        assertThat(monthIndexOf(YearMonth(2027, Month.JANUARY)))
            .isEqualTo(monthIndexOf(YearMonth(2026, Month.DECEMBER)) + 1)
    }

    @Test
    fun `index round-trips back to its month`() {
        listOf(
            YearMonth(1900, Month.JANUARY),
            jun26,
            YearMonth(2026, Month.DECEMBER),
            YearMonth(2100, Month.DECEMBER),
        ).forEach { ym ->
            assertThat(yearMonthForIndex(monthIndexOf(ym))).isEqualTo(ym)
        }
    }

    @Test
    fun `indices are non-negative and span 1900 through 2100`() {
        assertThat(monthIndexOf(YearMonth(1900, Month.JANUARY))).isEqualTo(0)
        assertThat(monthIndexOf(jun26)).isGreaterThan(0)
        assertThat(monthIndexOf(YearMonth(2100, Month.DECEMBER)))
            .isEqualTo(continuousMonthCount() - 1)
        assertThat(continuousMonthCount()).isEqualTo(201 * 12)
    }

    @Test
    fun `a month maps to its header item and back`() {
        val index = monthIndexOf(jun26)
        val item = itemIndexForMonth(index)
        // Both of a month's items — its sticky header and the block under it —
        // resolve back to the same month, so a title read off the first visible
        // item is right wherever the viewport sits inside the block.
        assertThat(monthIndexForItem(item)).isEqualTo(index)
        assertThat(monthIndexForItem(item + 1)).isEqualTo(index)
        assertThat(monthIndexForItem(item + 2)).isEqualTo(index + 1)
    }

    @Test
    fun `week rows follow the month's shape and its week start`() {
        // June 2026 starts on a Monday: 30 days → 5 rows either way.
        assertThat(weekRowsInMonth(jun26, DayOfWeek.MONDAY)).isEqualTo(5)
        assertThat(weekRowsInMonth(jun26, DayOfWeek.SUNDAY)).isEqualTo(5)
        // February 2026 starts on a Sunday: a Sunday-anchored grid fits it in 4
        // rows, a Monday-anchored one needs 5 — the block's height moves with the
        // preference even though its position in the list doesn't.
        val feb26 = YearMonth(2026, Month.FEBRUARY)
        assertThat(weekRowsInMonth(feb26, DayOfWeek.SUNDAY)).isEqualTo(4)
        assertThat(weekRowsInMonth(feb26, DayOfWeek.MONDAY)).isEqualTo(5)
        // February 2021 starts on a Monday and is 28 days → exactly 4.
        assertThat(weekRowsInMonth(YearMonth(2021, Month.FEBRUARY), DayOfWeek.MONDAY))
            .isEqualTo(4)
    }

    @Test
    fun `the placeholder block is the size the loaded one will be`() {
        // Otherwise scrolling across an unloaded month jumps when its data lands.
        (0 until 24).forEach { offset ->
            val ym = yearMonthForIndex(monthIndexOf(jun26) + offset)
            DayOfWeek.entries.forEach { ws ->
                assertThat(weekRowsInMonth(ym, ws))
                    .isEqualTo(layoutMonthWeeks(ym, ws, emptyList(), TimeZone.UTC).size)
            }
        }
    }

    @Test
    fun `a window is clamped to the months that exist`() {
        // The regression this guards: an empty visible-items list reported as
        // "month 0", whose padded window ran to -4 — and yearMonthForIndex has
        // no month for that, so the whole grid failed with "couldn't read".
        assertThat(clampMonthWindow(-4..4)).isEqualTo(0..4)
        val last = continuousMonthCount() - 1
        assertThat(clampMonthWindow((last - 2)..(last + 4))).isEqualTo((last - 2)..last)
        assertThat(clampMonthWindow(10..20)).isEqualTo(10..20)
    }

    @Test
    fun `every index a clamped window can hold resolves to a month`() {
        listOf(clampMonthWindow(-4..4), clampMonthWindow((continuousMonthCount() - 1)..(continuousMonthCount() + 3)))
            .forEach { window ->
                window.forEach { index -> yearMonthForIndex(index) }
            }
    }

    @Test
    fun `the dense week window covers every week its months touch`() {
        // One month-window load has to lay out every Dense row those months
        // appear in, boundary weeks included, or rows would flicker as
        // placeholders while the data for them is already in hand.
        DayOfWeek.entries.forEach { ws ->
            val window = monthIndexOf(jun26)..monthIndexOf(YearMonth(2026, Month.AUGUST))
            val weeks = weekWindowFor(window, ws)
            window.forEach { index ->
                val ym = yearMonthForIndex(index)
                val first = weekIndexOf(firstOfMonth(ym), ws)
                val last = weekIndexOf(firstOfMonth(yearMonthForIndex(index + 1)).minus(1, DateTimeUnit.DAY), ws)
                assertThat(weeks.contains(first)).isTrue()
                assertThat(weeks.contains(last)).isTrue()
            }
        }
    }

    @Test
    fun `a dense row reports the month that owns most of it`() {
        // June 29 – July 5, 2026 (Monday-anchored): four of its days are July's,
        // so the window follows July rather than snapping back to June.
        val boundary = weekIndexOf(LocalDate(2026, 6, 29), DayOfWeek.MONDAY)
        assertThat(monthIndexForWeek(boundary, DayOfWeek.MONDAY))
            .isEqualTo(monthIndexOf(YearMonth(2026, Month.JULY)))
        // A row wholly inside June reports June.
        assertThat(monthIndexForWeek(weekIndexOf(LocalDate(2026, 6, 15), DayOfWeek.MONDAY), DayOfWeek.MONDAY))
            .isEqualTo(monthIndexOf(jun26))
    }

    @Test
    fun `every day of a dense week shares one index`() {
        val mon = LocalDate(2026, 6, 8)
        val indices = (0..6).map { weekIndexOf(mon.plus(it, DateTimeUnit.DAY), DayOfWeek.MONDAY) }
        assertThat(indices.toSet()).hasSize(1)
    }

    @Test
    fun `dense week indices round-trip and stay inside the list`() {
        val wed = LocalDate(2026, 6, 10)
        DayOfWeek.entries.forEach { ws ->
            val index = weekIndexOf(wed, ws)
            assertThat(weekStartForIndex(index, ws)).isEqualTo(wed.startOfGridWeek(ws))
            assertThat(weekIndexOf(LocalDate(1900, 1, 1), ws)).isAtLeast(0)
            assertThat(index).isLessThan(continuousWeekCount(ws))
            assertThat(weekIndexOf(LocalDate(2100, 12, 31), ws))
                .isLessThan(continuousWeekCount(ws))
        }
    }

    @Test
    fun `firstOfMonth is the 1st`() {
        assertThat(firstOfMonth(jun26)).isEqualTo(LocalDate(2026, 6, 1))
    }

    @Test
    fun `a window comfortably around the visible range is kept`() {
        assertThat(nextLoadWindow(loaded = 0..100, firstVisible = 40, lastVisible = 45)).isNull()
    }

    @Test
    fun `nearing a loaded edge widens the window around the visible range`() {
        // Within two months of the top edge → reload, padded on both sides.
        assertThat(nextLoadWindow(loaded = 0..100, firstVisible = 1, lastVisible = 2))
            .isEqualTo(-4..7)

        assertThat(nextLoadWindow(loaded = 0..100, firstVisible = 98, lastVisible = 100))
            .isEqualTo(93..105)
    }

    @Test
    fun `a jump far outside the window reloads around the destination`() {
        assertThat(nextLoadWindow(loaded = 0..100, firstVisible = 500, lastVisible = 501))
            .isEqualTo(495..506)
    }

    @Test
    fun `the reloaded window always clears the trigger it just crossed`() {
        // Otherwise every scroll frame would re-trigger a query — and with the
        // window growing from a pad of 1, this has to hold at every rung of the
        // ladder, not just the widest one.
        (1..8).forEach { pad ->
            val loaded = (10 - pad)..(10 + pad)
            val past = 10 + pad + 1
            val widened = nextLoadWindow(loaded, past, past, pad)!!
            assertThat(nextLoadWindow(widened, past, past, pad)).isNull()
        }
    }

    @Test
    fun `the reload trigger stays inside the pad`() {
        // A trigger at or beyond the pad would fire again the instant its own
        // reload landed, and the window would query forever.
        (1..8).forEach { pad -> assertThat(edgeForPad(pad)).isLessThan(pad) }
        // A one-month window has no room for hysteresis: reload only on contact.
        assertThat(edgeForPad(1)).isEqualTo(0)
        assertThat(edgeForPad(0)).isEqualTo(0)
    }

    @Test
    fun `the smallest window reloads on crossing rather than nearing its edge`() {
        // The first frame loads one month either side of today, which leaves no
        // room to reload *before* the edge: at pad 1 the trigger is contact.
        // Only momentary — the first completed load widens the pad to 3, which
        // buys the usual head start back.
        val initial = 10..12
        assertThat(nextLoadWindow(initial, firstVisible = 12, lastVisible = 12, pad = 1)).isNull()
        assertThat(nextLoadWindow(initial, firstVisible = 13, lastVisible = 13, pad = 1))
            .isEqualTo(12..14)
    }

    @Test
    fun `the split agenda starts at today in the current month`() {
        val today = LocalDate(2026, 6, 10)
        assertThat(agendaStartForMonth(jun26, today)).isEqualTo(today)
    }

    @Test
    fun `the split agenda starts at the 1st of another month`() {
        val today = LocalDate(2026, 6, 10)
        assertThat(agendaStartForMonth(YearMonth(2026, Month.JULY), today))
            .isEqualTo(LocalDate(2026, 7, 1))
        assertThat(agendaStartForMonth(YearMonth(2025, Month.JUNE), today))
            .isEqualTo(LocalDate(2025, 6, 1))
    }
}
