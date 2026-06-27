package de.jeanlucmakiola.calendula.ui.agenda

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class AgendaRangeTest {

    @Test
    fun `fixed ranges have 1, 7, 30 day counts`() {
        assertThat(AgendaRange.Day.dayCount()).isEqualTo(1)
        assertThat(AgendaRange.Week.dayCount()).isEqualTo(7)
        assertThat(AgendaRange.Month.dayCount()).isEqualTo(30)
    }

    @Test
    fun `custom day count is clamped to bounds`() {
        assertThat(AgendaRange.Custom(45).dayCount()).isEqualTo(45)
        assertThat(AgendaRange.Custom(0).dayCount()).isEqualTo(AgendaRange.MIN_CUSTOM_DAYS)
        assertThat(AgendaRange.Custom(9_999).dayCount()).isEqualTo(AgendaRange.MAX_CUSTOM_DAYS)
    }

    @Test
    fun `fixed ranges round-trip through storage`() {
        listOf(AgendaRange.Day, AgendaRange.Week, AgendaRange.Month).forEach { range ->
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
