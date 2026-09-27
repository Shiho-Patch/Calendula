package de.jeanlucmakiola.calendula.data.reminders

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
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

    /** Wrapper defaulting `today` to the event's own begin day, so unrelated tests read "today". */
    private fun text(
        beginMillis: Long,
        endMillis: Long,
        isAllDay: Boolean = false,
        zone: ZoneId = berlin,
        locale: Locale = Locale.GERMANY,
        is24Hour: Boolean = true,
        today: LocalDate? = null,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ): String = reminderTimeText(
        beginMillis = beginMillis,
        endMillis = endMillis,
        isAllDay = isAllDay,
        zone = zone,
        locale = locale,
        is24Hour = is24Hour,
        today = today ?: java.time.Instant.ofEpochMilli(beginMillis).atZone(zone).toLocalDate(),
        firstDayOfWeek = firstDayOfWeek,
        todayLabel = "Today",
        tomorrowLabel = "Tomorrow",
        yesterdayLabel = "Yesterday",
    )

    @Test
    fun `timed event today shows just the time range`() {
        val text = text(
            beginMillis = millisAt(LocalDateTime.of(2026, 6, 11, 9, 30), berlin),
            endMillis = millisAt(LocalDateTime.of(2026, 6, 11, 10, 0), berlin),
        )
        assertThat(text).isEqualTo("09:30 – 10:00")
    }

    @Test
    fun `12-hour preference renders an am-pm time range`() {
        val text = text(
            beginMillis = millisAt(LocalDateTime.of(2026, 6, 11, 14, 0), berlin),
            endMillis = millisAt(LocalDateTime.of(2026, 6, 11, 15, 0), berlin),
            locale = Locale.US,
            is24Hour = false,
        )
        assertThat(text).isEqualTo("2:00 PM – 3:00 PM")
    }

    @Test
    fun `tomorrow's event is prefixed with the tomorrow label`() {
        val text = text(
            beginMillis = millisAt(LocalDateTime.of(2026, 6, 12, 9, 30), berlin),
            endMillis = millisAt(LocalDateTime.of(2026, 6, 12, 10, 0), berlin),
            today = LocalDate.of(2026, 6, 11),
        )
        assertThat(text).isEqualTo("Tomorrow, 09:30 – 10:00")
    }

    @Test
    fun `yesterday's event is prefixed with the yesterday label`() {
        val text = text(
            beginMillis = millisAt(LocalDateTime.of(2026, 6, 10, 9, 30), berlin),
            endMillis = millisAt(LocalDateTime.of(2026, 6, 10, 10, 0), berlin),
            today = LocalDate.of(2026, 6, 11),
        )
        assertThat(text).isEqualTo("Yesterday, 09:30 – 10:00")
    }

    @Test
    fun `an event later this week is prefixed with the short weekday`() {
        // 2026-06-11 is a Thursday; +3 days lands on Sunday, still this (Mon-based) week.
        val text = text(
            beginMillis = millisAt(LocalDateTime.of(2026, 6, 14, 9, 30), berlin),
            endMillis = millisAt(LocalDateTime.of(2026, 6, 14, 10, 0), berlin),
            today = LocalDate.of(2026, 6, 11),
        )
        assertThat(text).isEqualTo("So., 09:30 – 10:00")
    }

    @Test
    fun `the week boundary honours the week-start setting`() {
        // Today Thu 2026-06-11, event Sun 2026-06-14. With a Sunday-start week the
        // Thursday's week runs Sun 06-07..Sat 06-13, so 06-14 is already next week
        // and must render as a date — the opposite of the Monday-start case above.
        val text = text(
            beginMillis = millisAt(LocalDateTime.of(2026, 6, 14, 9, 30), berlin),
            endMillis = millisAt(LocalDateTime.of(2026, 6, 14, 10, 0), berlin),
            today = LocalDate.of(2026, 6, 11),
            firstDayOfWeek = DayOfWeek.SUNDAY,
        )
        assertThat(text).isEqualTo("14.06.2026, 09:30 – 10:00")
    }

    @Test
    fun `an event next week falls back to the exact date, not a weekday`() {
        // 2026-06-15 is the following Monday — a weekday alone would be ambiguous.
        val text = text(
            beginMillis = millisAt(LocalDateTime.of(2026, 6, 15, 9, 30), berlin),
            endMillis = millisAt(LocalDateTime.of(2026, 6, 15, 10, 0), berlin),
            today = LocalDate.of(2026, 6, 11),
        )
        assertThat(text).isEqualTo("15.06.2026, 09:30 – 10:00")
    }

    @Test
    fun `timed event crossing midnight includes both dates`() {
        val text = text(
            beginMillis = millisAt(LocalDateTime.of(2026, 6, 11, 23, 30), berlin),
            endMillis = millisAt(LocalDateTime.of(2026, 6, 12, 0, 30), berlin),
        )
        assertThat(text).contains("11.06.2026")
        assertThat(text).contains("12.06.2026")
        assertThat(text).contains("23:30")
    }

    @Test
    fun `all-day single day today reads as today, read in UTC`() {
        val text = text(
            beginMillis = utcMidnight(LocalDate.of(2026, 6, 11)),
            endMillis = utcMidnight(LocalDate.of(2026, 6, 12)),
            isAllDay = true,
            // Zone must not matter for all-day events: UTC midnight is
            // the previous evening in Los Angeles — naive local reading would shift the day.
            zone = ZoneId.of("America/Los_Angeles"),
            today = LocalDate.of(2026, 6, 11),
        )
        assertThat(text).isEqualTo("Today")
    }

    @Test
    fun `all-day event tomorrow reads as tomorrow`() {
        val text = text(
            beginMillis = utcMidnight(LocalDate.of(2026, 6, 12)),
            endMillis = utcMidnight(LocalDate.of(2026, 6, 13)),
            isAllDay = true,
            today = LocalDate.of(2026, 6, 11),
        )
        assertThat(text).isEqualTo("Tomorrow")
    }

    @Test
    fun `all-day event later this week reads as the short weekday`() {
        // 2026-06-11 is a Thursday; Saturday the 13th shares its week.
        val text = text(
            beginMillis = utcMidnight(LocalDate.of(2026, 6, 13)),
            endMillis = utcMidnight(LocalDate.of(2026, 6, 14)),
            isAllDay = true,
            locale = Locale.US,
            today = LocalDate.of(2026, 6, 11),
        )
        assertThat(text).isEqualTo("Sat")
    }

    @Test
    fun `all-day event next week falls back to the exact date`() {
        val text = text(
            beginMillis = utcMidnight(LocalDate.of(2026, 6, 16)),
            endMillis = utcMidnight(LocalDate.of(2026, 6, 17)),
            isAllDay = true,
            today = LocalDate.of(2026, 6, 11),
        )
        assertThat(text).isEqualTo("16.06.2026")
    }

    @Test
    fun `all-day multi-day reads relative start and the last covered day`() {
        val text = text(
            beginMillis = utcMidnight(LocalDate.of(2026, 6, 12)),
            endMillis = utcMidnight(LocalDate.of(2026, 6, 14)),
            isAllDay = true,
            today = LocalDate.of(2026, 6, 11),
        )
        assertThat(text).isEqualTo("Tomorrow – 13.06.2026")
    }

    @Test
    fun `all-day multi-day further out keeps both dates`() {
        val text = text(
            beginMillis = utcMidnight(LocalDate.of(2026, 7, 1)),
            endMillis = utcMidnight(LocalDate.of(2026, 7, 3)),
            isAllDay = true,
            today = LocalDate.of(2026, 6, 11),
        )
        assertThat(text).isEqualTo("01.07.2026 – 02.07.2026")
    }

    @Test
    fun `degenerate all-day range never renders an inverted span`() {
        val day = utcMidnight(LocalDate.of(2026, 6, 11))
        val text = text(
            beginMillis = day,
            endMillis = day,
            isAllDay = true,
            today = LocalDate.of(2026, 6, 11),
        )
        assertThat(text).isEqualTo("Today")
    }
}
