package de.jeanlucmakiola.calendula.domain

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant
import org.junit.jupiter.api.Test

/**
 * The all-day day-boundary rule every surface shares. All-day events are stored
 * at UTC midnights with an exclusive end, so their dates must be resolved in UTC
 * whatever the device zone is — reading them in the device zone names the wrong
 * day on both sides of the meridian (#65 east, #82 west).
 */
class EventInstanceSpanTest {

    private val berlin = TimeZone.of("Europe/Berlin") // UTC+2 in July
    private val newYork = TimeZone.of("America/New_York") // UTC-4 in July

    /** 19 July 2026, all day: UTC midnight to the exclusive next UTC midnight. */
    private fun allDayJul19(): EventInstance = instance(
        start = utc(2026, 7, 19),
        end = utc(2026, 7, 20),
        isAllDay = true,
    )

    private fun utc(y: Int, mo: Int, d: Int, h: Int = 0): Instant =
        LocalDateTime(y, mo, d, h, 0).toInstant(TimeZone.UTC)

    private fun instance(start: Instant, end: Instant, isAllDay: Boolean) = EventInstance(
        instanceId = 1L,
        eventId = 1L,
        calendarId = 1L,
        title = "Event",
        start = start,
        end = end,
        isAllDay = isAllDay,
        color = 0xFF000000.toInt(),
        location = null,
    )

    @Test
    fun `all-day event keeps its date west of UTC`() {
        // Regression for #82: 00:00 UTC on the 19th is 20:00 on the 18th in New
        // York, so resolving in the device zone would name the 18th.
        val event = allDayJul19()
        assertThat(event.spanFirstDay(newYork)).isEqualTo(LocalDate(2026, 7, 19))
        assertThat(event.spanLastDay(newYork)).isEqualTo(LocalDate(2026, 7, 19))
        assertThat(event.spansMultipleDays(newYork)).isFalse()
    }

    @Test
    fun `all-day event keeps its date east of UTC`() {
        // Regression for #65: the exclusive end dips past local midnight in
        // Berlin, which would leak the event onto the 20th.
        val event = allDayJul19()
        assertThat(event.spanFirstDay(berlin)).isEqualTo(LocalDate(2026, 7, 19))
        assertThat(event.spanLastDay(berlin)).isEqualTo(LocalDate(2026, 7, 19))
        assertThat(event.spansMultipleDays(berlin)).isFalse()
    }

    @Test
    fun `multi-day all-day event ends on its last covered day`() {
        val event = instance(utc(2026, 7, 19), utc(2026, 7, 22), isAllDay = true)
        assertThat(event.spanFirstDay(newYork)).isEqualTo(LocalDate(2026, 7, 19))
        assertThat(event.spanLastDay(newYork)).isEqualTo(LocalDate(2026, 7, 21))
        assertThat(event.spansMultipleDays(newYork)).isTrue()
    }

    @Test
    fun `timed event resolves in the device zone`() {
        // 23:30 UTC on the 19th is already the 20th in Berlin and still the 19th
        // in New York — a timed event follows the device zone, unlike all-day.
        val event = instance(utc(2026, 7, 19, 23), utc(2026, 7, 20, 1), isAllDay = false)
        assertThat(event.spanFirstDay(berlin)).isEqualTo(LocalDate(2026, 7, 20))
        assertThat(event.spanFirstDay(newYork)).isEqualTo(LocalDate(2026, 7, 19))
    }

    @Test
    fun `zero-length event occupies its start day`() {
        val event = instance(utc(2026, 7, 19, 12), utc(2026, 7, 19, 12), isAllDay = false)
        assertThat(event.spanLastDay(newYork)).isEqualTo(LocalDate(2026, 7, 19))
    }

    @Test
    fun `dateZone pins all-day events to UTC and leaves timed events alone`() {
        assertThat(allDayJul19().dateZone(newYork)).isEqualTo(TimeZone.UTC)
        val timed = instance(utc(2026, 7, 19, 12), utc(2026, 7, 19, 13), isAllDay = false)
        assertThat(timed.dateZone(newYork)).isEqualTo(newYork)
    }
}
