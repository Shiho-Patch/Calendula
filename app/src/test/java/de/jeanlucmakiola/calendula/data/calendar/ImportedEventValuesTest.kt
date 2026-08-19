package de.jeanlucmakiola.calendula.data.calendar

import android.provider.CalendarContract
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.EventColorOption
import de.jeanlucmakiola.calendula.domain.ics.ParsedIcsEvent
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class ImportedEventValuesTest {

    private val dayStart = Instant.parse("2026-08-19T00:00:00Z")

    private fun allDay(
        rrule: String? = null,
        exDates: List<String> = emptyList(),
        color: Int? = null,
        days: Int = 1,
    ) = ParsedIcsEvent(
        uid = "u@x",
        summary = "Anna Birthday",
        start = dayStart,
        end = Instant.fromEpochMilliseconds(dayStart.toEpochMilliseconds() + days * 86_400_000L),
        isAllDay = true,
        zoneId = "UTC",
        recurrenceRule = rrule,
        exDates = exDates,
        color = color,
    )

    private fun values(event: ParsedIcsEvent, palette: List<EventColorOption> = emptyList()) =
        buildImportedEventValues(event, calendarId = 7L, uid = "u@x", palette = palette)

    @Test
    fun `a one-off carries DTEND and no recurrence`() {
        val v = values(allDay())
        assertThat(v[CalendarContract.Events.DTEND])
            .isEqualTo(dayStart.toEpochMilliseconds() + 86_400_000L)
        assertThat(v).doesNotContainKey(CalendarContract.Events.DURATION)
        assertThat(v[CalendarContract.Events.ALL_DAY]).isEqualTo(1)
        assertThat(v[CalendarContract.Events.EVENT_TIMEZONE]).isEqualTo("UTC")
    }

    @Test
    fun `a recurring row carries DURATION instead of DTEND`() {
        val v = values(allDay(rrule = "FREQ=YEARLY;INTERVAL=1"))
        assertThat(v).doesNotContainKey(CalendarContract.Events.DTEND)
        assertThat(v[CalendarContract.Events.RRULE]).isEqualTo("FREQ=YEARLY;INTERVAL=1")
        assertThat(v[CalendarContract.Events.DURATION]).isEqualTo("P1D")
    }

    @Test
    fun `an all-day series is never zero days long`() {
        // A degenerate span would expand into no instances at all — the series
        // would simply not exist (Codeberg #225).
        val v = values(allDay(rrule = "FREQ=YEARLY;INTERVAL=1", days = 0))
        assertThat(v[CalendarContract.Events.DURATION]).isEqualTo("P1D")
    }

    @Test
    fun `EXDATEs ride along with the recurrence`() {
        val v = values(allDay(rrule = "FREQ=DAILY", exDates = listOf("20260820", "20260822")))
        assertThat(v[CalendarContract.Events.EXDATE]).isEqualTo("20260820,20260822")
    }

    @Test
    fun `EXDATEs are not written without a recurrence to exclude from`() {
        val v = values(allDay(exDates = listOf("20260820")))
        assertThat(v).doesNotContainKey(CalendarContract.Events.EXDATE)
    }

    @Test
    fun `an imported colour is written raw when the account publishes no palette`() {
        val v = values(allDay(color = -2818048))
        assertThat(v[CalendarContract.Events.EVENT_COLOR]).isEqualTo(-2818048)
        assertThat(v[CalendarContract.Events.EVENT_COLOR_KEY]).isNull()
    }

    @Test
    fun `an imported colour snaps to the nearest published palette key`() {
        val palette = listOf(
            EventColorOption(key = "1", argb = 0xFF0000FF.toInt()), // blue
            EventColorOption(key = "2", argb = 0xFFFF4500.toInt()), // orangered
            EventColorOption(key = "3", argb = 0xFF008000.toInt()), // green
        )
        // Tomato — visually an orangered, nothing like the blue or the green.
        val v = values(allDay(color = 0xFFFF6347.toInt()), palette)

        assertThat(v[CalendarContract.Events.EVENT_COLOR_KEY]).isEqualTo("2")
        // A raw colour alongside a key is what a palette calendar rejects.
        assertThat(v).doesNotContainKey(CalendarContract.Events.EVENT_COLOR)
    }

    @Test
    fun `an event with no colour touches neither colour column`() {
        val v = values(allDay())
        assertThat(v).doesNotContainKey(CalendarContract.Events.EVENT_COLOR)
        assertThat(v).doesNotContainKey(CalendarContract.Events.EVENT_COLOR_KEY)
    }

    @Test
    fun `a timed event keeps its own zone and a seconds duration`() {
        val start = Instant.parse("2026-08-19T08:00:00Z")
        val event = ParsedIcsEvent(
            uid = "u@x",
            summary = "Standup",
            start = start,
            end = Instant.parse("2026-08-19T08:15:00Z"),
            isAllDay = false,
            zoneId = "Europe/Berlin",
            recurrenceRule = "FREQ=WEEKLY",
        )

        val v = values(event)
        assertThat(v[CalendarContract.Events.EVENT_TIMEZONE]).isEqualTo("Europe/Berlin")
        assertThat(v[CalendarContract.Events.DURATION]).isEqualTo("P900S")
        assertThat(v[CalendarContract.Events.ALL_DAY]).isEqualTo(0)
    }
}
