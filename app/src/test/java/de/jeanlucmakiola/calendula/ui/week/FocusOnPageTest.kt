package de.jeanlucmakiola.calendula.ui.week

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

/** Where the focused date goes as a timeline pages (#184). */
class FocusOnPageTest {

    // 2026-09-30 is a Wednesday.
    private val wednesday = LocalDate(2026, 9, 30)

    @Test
    fun `a page that still shows the focus keeps it`() {
        val grid = WeekGrid(DayOfWeek.MONDAY)
        assertThat(grid.focusOnPage(wednesday, grid.pageFor(wednesday))).isEqualTo(wednesday)
    }

    @Test
    fun `a week on keeps the weekday`() {
        val grid = WeekGrid(DayOfWeek.MONDAY)
        val page = grid.pageFor(wednesday)
        assertThat(grid.focusOnPage(wednesday, page + 1)).isEqualTo(LocalDate(2026, 10, 7))
        assertThat(grid.focusOnPage(wednesday, page - 2)).isEqualTo(LocalDate(2026, 9, 16))
    }

    @Test
    fun `a run of days keeps the focus's column`() {
        val grid = DaysGrid.startingAt(LocalDate(2026, 9, 29), 3)
        val page = grid.pageFor(wednesday)
        assertThat(grid.focusOnPage(wednesday, page + 1)).isEqualTo(LocalDate(2026, 10, 3))
    }

    @Test
    fun `a day page is the focus`() {
        val page = DayGrid.pageFor(wednesday)
        assertThat(DayGrid.focusOnPage(wednesday, page + 5)).isEqualTo(LocalDate(2026, 10, 5))
    }
}
