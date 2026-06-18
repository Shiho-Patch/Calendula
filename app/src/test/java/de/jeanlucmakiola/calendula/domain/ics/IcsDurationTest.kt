package de.jeanlucmakiola.calendula.domain.ics

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class IcsDurationTest {

    @Test
    fun `parses the single-unit forms Calendula writes plus general ones`() {
        assertThat(parseRfc2445DurationMillis("P1D")).isEqualTo(86_400_000L)
        assertThat(parseRfc2445DurationMillis("P3600S")).isEqualTo(3_600_000L)
        assertThat(parseRfc2445DurationMillis("PT1H30M")).isEqualTo(5_400_000L)
        assertThat(parseRfc2445DurationMillis("P1W")).isEqualTo(604_800_000L)
    }

    @Test
    fun `is sign-aware for before-start VALARM triggers`() {
        assertThat(parseRfc2445DurationMillis("-PT15M")).isEqualTo(-900_000L)
        assertThat(parseRfc2445DurationMillis("PT0M")).isEqualTo(0L)
    }

    @Test
    fun `unparseable input is zero`() {
        assertThat(parseRfc2445DurationMillis(null)).isEqualTo(0L)
        assertThat(parseRfc2445DurationMillis("")).isEqualTo(0L)
        assertThat(parseRfc2445DurationMillis("nonsense")).isEqualTo(0L)
    }
}
