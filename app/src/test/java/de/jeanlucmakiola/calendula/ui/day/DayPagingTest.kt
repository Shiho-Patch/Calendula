package de.jeanlucmakiola.calendula.ui.day

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

class DayPagingTest {

    @Test
    fun `a page and its day round-trip`() {
        listOf(LocalDate(2026, 9, 27), LocalDate(1990, 5, 16), LocalDate(2100, 2, 28)).forEach { day ->
            assertThat(dayForPage(dayPageFor(day))).isEqualTo(day)
        }
    }

    @Test
    fun `adjacent pages are adjacent days across a month end`() {
        val page = dayPageFor(LocalDate(2026, 2, 28))
        assertThat(dayForPage(page + 1)).isEqualTo(LocalDate(2026, 3, 1))
        assertThat(dayForPage(page - 1)).isEqualTo(LocalDate(2026, 2, 27))
    }
}
