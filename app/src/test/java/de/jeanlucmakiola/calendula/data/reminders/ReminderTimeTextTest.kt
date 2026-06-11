package de.jeanlucmakiola.calendula.data.reminders

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class ReminderTimeTextTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    private fun millisAt(dateTime: LocalDateTime, zone: ZoneId): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    private fun utcMidnight(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `timed event on one day shows just the time range`() {
        val text = reminderTimeText(
            beginMillis = millisAt(LocalDateTime.of(2026, 6, 11, 9, 30), berlin),
            endMillis = millisAt(LocalDateTime.of(2026, 6, 11, 10, 0), berlin),
            isAllDay = false,
            zone = berlin,
            locale = Locale.GERMANY,
        )
        assertThat(text).isEqualTo("09:30 – 10:00")
    }

    @Test
    fun `timed event crossing midnight includes both dates`() {
        val text = reminderTimeText(
            beginMillis = millisAt(LocalDateTime.of(2026, 6, 11, 23, 30), berlin),
            endMillis = millisAt(LocalDateTime.of(2026, 6, 12, 0, 30), berlin),
            isAllDay = false,
            zone = berlin,
            locale = Locale.GERMANY,
        )
        assertThat(text).contains("11.06.2026")
        assertThat(text).contains("12.06.2026")
        assertThat(text).contains("23:30")
    }

    @Test
    fun `all-day single day shows one date, read in UTC`() {
        val text = reminderTimeText(
            beginMillis = utcMidnight(LocalDate.of(2026, 6, 11)),
            endMillis = utcMidnight(LocalDate.of(2026, 6, 12)),
            isAllDay = true,
            // Zone must not matter for all-day events: UTC midnight is
            // 02:00 in Berlin — naive local reading would shift the day.
            zone = berlin,
            locale = Locale.GERMANY,
        )
        assertThat(text).isEqualTo("11.06.2026")
    }

    @Test
    fun `all-day multi-day shows the last covered day, not the exclusive end`() {
        val text = reminderTimeText(
            beginMillis = utcMidnight(LocalDate.of(2026, 6, 11)),
            endMillis = utcMidnight(LocalDate.of(2026, 6, 13)),
            isAllDay = true,
            zone = berlin,
            locale = Locale.GERMANY,
        )
        assertThat(text).isEqualTo("11.06.2026 – 12.06.2026")
    }

    @Test
    fun `degenerate all-day range never renders an inverted span`() {
        val day = utcMidnight(LocalDate.of(2026, 6, 11))
        val text = reminderTimeText(
            beginMillis = day,
            endMillis = day,
            isAllDay = true,
            zone = berlin,
            locale = Locale.GERMANY,
        )
        assertThat(text).isEqualTo("11.06.2026")
    }
}
