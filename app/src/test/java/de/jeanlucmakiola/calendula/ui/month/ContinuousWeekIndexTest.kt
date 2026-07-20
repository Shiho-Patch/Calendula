package de.jeanlucmakiola.calendula.ui.month

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.jupiter.api.Test

/**
 * The continuous grid's coordinate space. Every row's identity — and the
 * LazyColumn item it maps to — hangs off this arithmetic, so it gets its own
 * tests rather than being exercised only through the UI.
 */
class ContinuousWeekIndexTest {

    // 2026-06-08 is a Monday; 2026-06-10 the Wednesday of the same week.
    private val mon = LocalDate(2026, 6, 8)
    private val wed = LocalDate(2026, 6, 10)

    @Test
    fun `every day of a week shares one index`() {
        val indices = (0..6).map { weekIndexOf(mon.plus(it, DateTimeUnit.DAY), DayOfWeek.MONDAY) }
        assertThat(indices.toSet()).hasSize(1)
    }

    @Test
    fun `consecutive weeks are consecutive indices`() {
        val a = weekIndexOf(mon, DayOfWeek.MONDAY)
        val b = weekIndexOf(mon.plus(7, DateTimeUnit.DAY), DayOfWeek.MONDAY)
        val back = weekIndexOf(mon.plus(-7, DateTimeUnit.DAY), DayOfWeek.MONDAY)
        assertThat(b).isEqualTo(a + 1)
        assertThat(back).isEqualTo(a - 1)
    }

    @Test
    fun `index round-trips back to the week's first day`() {
        DayOfWeek.entries.forEach { ws ->
            val index = weekIndexOf(wed, ws)
            assertThat(weekStartForIndex(index, ws)).isEqualTo(wed.startOfGridWeek(ws))
        }
    }

    @Test
    fun `the week start shifts which week a boundary day belongs to`() {
        // Sunday the 14th closes the Monday-anchored week but opens the Sunday one.
        val sun = LocalDate(2026, 6, 14)
        assertThat(weekIndexOf(sun, DayOfWeek.MONDAY))
            .isEqualTo(weekIndexOf(mon, DayOfWeek.MONDAY))
        assertThat(weekIndexOf(sun, DayOfWeek.SUNDAY))
            .isEqualTo(weekIndexOf(sun.plus(1, DateTimeUnit.DAY), DayOfWeek.SUNDAY))
    }

    @Test
    fun `indices are non-negative across the supported span`() {
        // The epoch sits before any date the grid scrolls to, so item indices and
        // week indices stay the same number — no offset to reconcile.
        DayOfWeek.entries.forEach { ws ->
            assertThat(weekIndexOf(LocalDate(1900, 1, 1), ws)).isAtLeast(0)
            assertThat(weekIndexOf(LocalDate(2026, 6, 8), ws)).isGreaterThan(0)
        }
    }

    @Test
    fun `the list spans 1900 through 2100`() {
        DayOfWeek.entries.forEach { ws ->
            val count = continuousWeekCount(ws)
            assertThat(weekIndexOf(LocalDate(2100, 12, 31), ws)).isLessThan(count)
            assertThat(weekIndexOf(LocalDate(2026, 6, 8), ws)).isLessThan(count)
            // ~200 years of weeks, give or take the anchor.
            assertThat(count).isIn(10_400..10_500)
        }
    }

    @Test
    fun `a window comfortably around the visible range is kept`() {
        assertThat(nextLoadWindow(loaded = 0..100, firstVisible = 40, lastVisible = 45)).isNull()
    }

    @Test
    fun `nearing a loaded edge widens the window around the visible range`() {
        // Within four weeks of the top edge → reload, padded on both sides.
        val widened = nextLoadWindow(loaded = 0..100, firstVisible = 2, lastVisible = 7)
        assertThat(widened).isEqualTo(-10..19)

        val atBottom = nextLoadWindow(loaded = 0..100, firstVisible = 94, lastVisible = 99)
        assertThat(atBottom).isEqualTo(82..111)
    }

    @Test
    fun `a jump far outside the window reloads around the destination`() {
        assertThat(nextLoadWindow(loaded = 0..100, firstVisible = 500, lastVisible = 505))
            .isEqualTo(488..517)
    }

    @Test
    fun `the reloaded window always clears the trigger it just crossed`() {
        // Otherwise every scroll frame would re-trigger a query.
        var loaded = 0..100
        val window = nextLoadWindow(loaded, firstVisible = 2, lastVisible = 7)!!
        loaded = window
        assertThat(nextLoadWindow(loaded, firstVisible = 2, lastVisible = 7)).isNull()
    }
}
