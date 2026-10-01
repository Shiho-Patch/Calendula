package de.jeanlucmakiola.calendula.ui.week

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.junit.jupiter.api.Test

class WeekPagingTest {

    // 2026-09-27 is a Sunday.
    private val sunday = LocalDate(2026, 9, 27)

    @Test
    fun `a page and its week round-trip for every first day`() {
        DayOfWeek.entries.forEach { firstDay ->
            val page = weekPageFor(sunday, firstDay)
            val start = weekStartForPage(page, firstDay)
            assertThat(start).isEqualTo(sunday.startOfWeek(firstDay))
            assertThat(weekPageFor(start, firstDay)).isEqualTo(page)
        }
    }

    @Test
    fun `every day of a week maps to the same page`() {
        val start = sunday.startOfWeek(DayOfWeek.MONDAY)
        val pages = (0..6).map { weekPageFor(start.plus(it, DateTimeUnit.DAY), DayOfWeek.MONDAY) }
        assertThat(pages.toSet()).hasSize(1)
    }

    @Test
    fun `adjacent pages are adjacent weeks`() {
        val page = weekPageFor(sunday, DayOfWeek.SUNDAY)
        assertThat(weekStartForPage(page + 1, DayOfWeek.SUNDAY))
            .isEqualTo(LocalDate(2026, 10, 4))
        assertThat(weekStartForPage(page - 1, DayOfWeek.SUNDAY))
            .isEqualTo(LocalDate(2026, 9, 20))
    }

    @Test
    fun `dates before the epoch page resolve too`() {
        val old = LocalDate(1990, 5, 16)
        val page = weekPageFor(old, DayOfWeek.MONDAY)
        assertThat(weekStartForPage(page, DayOfWeek.MONDAY)).isEqualTo(LocalDate(1990, 5, 14))
    }

    @Test
    fun `multi-day pages begin on the selected date and advance by the chosen count`() {
        (3..6).forEach { count ->
            val grid = DaysGrid.startingAt(sunday, count)
            val page = grid.pageFor(sunday)
            assertThat(grid.startForPage(page)).isEqualTo(sunday)
            assertThat(grid.startForPage(page + 1)).isEqualTo(sunday.plus(count, DateTimeUnit.DAY))
            assertThat(grid.pageFor(sunday.plus(count - 1, DateTimeUnit.DAY))).isEqualTo(page)
        }
    }

    @Test
    fun `multi-day pages also work before the epoch`() {
        val date = LocalDate(1990, 5, 16)
        val grid = DaysGrid.startingAt(date, 3)
        val page = grid.pageFor(date)
        assertThat(grid.startForPage(page)).isEqualTo(date)
        assertThat(grid.startForPage(page - 1)).isEqualTo(LocalDate(1990, 5, 13))
    }

    @Test
    fun `a day page and its day round-trip`() {
        listOf(LocalDate(2026, 9, 27), LocalDate(1990, 5, 16), LocalDate(2100, 2, 28)).forEach { day ->
            assertThat(DayGrid.startForPage(DayGrid.pageFor(day))).isEqualTo(day)
        }
    }

    @Test
    fun `adjacent day pages are adjacent days across a month end`() {
        val page = DayGrid.pageFor(LocalDate(2026, 2, 28))
        assertThat(DayGrid.startForPage(page + 1)).isEqualTo(LocalDate(2026, 3, 1))
        assertThat(DayGrid.startForPage(page - 1)).isEqualTo(LocalDate(2026, 2, 27))
    }
}
