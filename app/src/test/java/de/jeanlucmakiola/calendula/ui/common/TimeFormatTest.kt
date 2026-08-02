package de.jeanlucmakiola.calendula.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.Locale

class TimeFormatTest {

    @Test
    fun `24-hour format is zero-padded HH mm`() {
        assertThat(formatTimeOfDay(9, 5, is24Hour = true, Locale.US)).isEqualTo("09:05")
        assertThat(formatTimeOfDay(14, 0, is24Hour = true, Locale.US)).isEqualTo("14:00")
        assertThat(formatTimeOfDay(0, 0, is24Hour = true, Locale.US)).isEqualTo("00:00")
    }

    @Test
    fun `12-hour format adds a localised am-pm marker`() {
        assertThat(formatTimeOfDay(14, 0, is24Hour = false, Locale.US)).isEqualTo("2:00 PM")
        assertThat(formatTimeOfDay(0, 30, is24Hour = false, Locale.US)).isEqualTo("12:30 AM")
        assertThat(formatTimeOfDay(12, 0, is24Hour = false, Locale.US)).isEqualTo("12:00 PM")
    }

    @Test
    fun `minute-of-day end-of-day renders 24 00 in 24h and midnight in 12h`() {
        assertThat(formatMinuteOfDay(1_440, is24Hour = true, Locale.US)).isEqualTo("24:00")
        assertThat(formatMinuteOfDay(1_440, is24Hour = false, Locale.US)).isEqualTo("12:00 AM")
        assertThat(formatMinuteOfDay(13 * 60 + 15, is24Hour = true, Locale.US)).isEqualTo("13:15")
    }

    @Test
    fun `gutter time drops the meridiem and clamps to the day`() {
        assertThat(formatGutterTime(9 * 60 + 15, is24Hour = true, Locale.US)).isEqualTo("09:15")
        assertThat(formatGutterTime(13 * 60 + 45, is24Hour = false, Locale.US)).isEqualTo("1:45")
        assertThat(formatGutterTime(0, is24Hour = false, Locale.US)).isEqualTo("12:00")
        assertThat(formatGutterTime(1_440, is24Hour = true, Locale.US)).isEqualTo("23:59")
    }

    @Test
    fun `hour label is zero-padded in 24h and compact am-pm in 12h`() {
        assertThat(formatHourLabel(13, is24Hour = true, Locale.US)).isEqualTo("13")
        assertThat(formatHourLabel(13, is24Hour = false, Locale.US)).isEqualTo("1 PM")
        assertThat(formatHourLabel(1, is24Hour = false, Locale.US)).isEqualTo("1 AM")
        assertThat(formatHourLabel(0, is24Hour = false, Locale.US)).isEqualTo("12 AM")
    }
}
