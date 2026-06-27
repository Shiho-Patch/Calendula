package de.jeanlucmakiola.calendula.ui.agenda

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test

class AgendaRangeTest {

    // A reference anchor: 2026-06-17 is a Wednesday.
    private val wednesday = LocalDate(2026, 6, 17)

    @Test
    fun `rolling ranges have fixed 1, 7, 30 day counts regardless of anchor`() {
        assertThat(AgendaRange.Day.dayCount(wednesday, DayOfWeek.MONDAY)).isEqualTo(1)
        assertThat(AgendaRange.Week.dayCount(wednesday, DayOfWeek.MONDAY)).isEqualTo(7)
        assertThat(AgendaRange.Month.dayCount(wednesday, DayOfWeek.MONDAY)).isEqualTo(30)
    }

    @Test
    fun `this week runs through the day before the week repeats`() {
        // Mon-start week: Wed → Wed,Thu,Fri,Sat,Sun = 5 days (everything before next Monday).
        assertThat(AgendaRange.ThisWeek.dayCount(wednesday, DayOfWeek.MONDAY)).isEqualTo(5)
        // On the first day of the week the whole 7 days remain.
        val monday = LocalDate(2026, 6, 15)
        assertThat(AgendaRange.ThisWeek.dayCount(monday, DayOfWeek.MONDAY)).isEqualTo(7)
        // A Sunday-start week shifts the boundary: Wed → 4 days left (through Saturday).
        assertThat(AgendaRange.ThisWeek.dayCount(wednesday, DayOfWeek.SUNDAY)).isEqualTo(4)
    }

    @Test
    fun `this month runs through the last day of the month`() {
        // June has 30 days: the 17th leaves 14 days (17..30 inclusive).
        assertThat(AgendaRange.ThisMonth.dayCount(wednesday, DayOfWeek.MONDAY)).isEqualTo(14)
        // February 2026 (non-leap) has 28 days.
        assertThat(AgendaRange.ThisMonth.dayCount(LocalDate(2026, 2, 20), DayOfWeek.MONDAY))
            .isEqualTo(9)
    }

    @Test
    fun `custom day count is clamped to bounds`() {
        assertThat(AgendaRange.Custom(45).dayCount(wednesday, DayOfWeek.MONDAY)).isEqualTo(45)
        assertThat(AgendaRange.Custom(0).dayCount(wednesday, DayOfWeek.MONDAY))
            .isEqualTo(AgendaRange.MIN_CUSTOM_DAYS)
        assertThat(AgendaRange.Custom(9_999).dayCount(wednesday, DayOfWeek.MONDAY))
            .isEqualTo(AgendaRange.MAX_CUSTOM_DAYS)
    }

    @Test
    fun `fixed ranges round-trip through storage`() {
        listOf(
            AgendaRange.Day,
            AgendaRange.Week,
            AgendaRange.Month,
            AgendaRange.ThisWeek,
            AgendaRange.ThisMonth,
        ).forEach { range ->
            assertThat(parseAgendaRange(range.storageValue(), default = AgendaRange.Day))
                .isEqualTo(range)
        }
    }

    @Test
    fun `custom range round-trips through storage`() {
        val range = AgendaRange.Custom(14)
        assertThat(range.storageValue()).isEqualTo("CUSTOM:14")
        assertThat(parseAgendaRange("CUSTOM:14", default = AgendaRange.Day))
            .isEqualTo(AgendaRange.Custom(14))
    }

    @Test
    fun `garbage and null fall back to the default`() {
        assertThat(parseAgendaRange(null, default = AgendaRange.Month)).isEqualTo(AgendaRange.Month)
        assertThat(parseAgendaRange("YEAR", default = AgendaRange.Week)).isEqualTo(AgendaRange.Week)
        assertThat(parseAgendaRange("CUSTOM:abc", default = AgendaRange.Day)).isEqualTo(AgendaRange.Day)
    }

    @Test
    fun `out-of-range custom value clamps on parse`() {
        assertThat(parseAgendaRange("CUSTOM:9999", default = AgendaRange.Day))
            .isEqualTo(AgendaRange.Custom(AgendaRange.MAX_CUSTOM_DAYS))
    }
}
