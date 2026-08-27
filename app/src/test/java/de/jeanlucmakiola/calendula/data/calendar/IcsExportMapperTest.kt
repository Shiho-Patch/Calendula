package de.jeanlucmakiola.calendula.data.calendar

import android.provider.CalendarContract
import com.google.common.truth.Truth.assertThat
import de.jeanlucmakiola.calendula.domain.EventStatus
import org.junit.jupiter.api.Test
import java.time.ZoneId

class IcsExportMapperTest {

    @Test
    fun `timed one-off row maps with its DTEND and kept UID`() {
        val reader = MapColumnReader(
            EventExportProjection.IDX_ID to 42L,
            EventExportProjection.IDX_UID to "abc@host",
            EventExportProjection.IDX_TITLE to "Standup",
            EventExportProjection.IDX_DTSTART to 1_000_000L,
            EventExportProjection.IDX_DTEND to 1_900_000L,
            EventExportProjection.IDX_ALL_DAY to 0,
            EventExportProjection.IDX_EVENT_TIMEZONE to "Europe/Berlin",
            EventExportProjection.IDX_AVAILABILITY to CalendarContract.Events.AVAILABILITY_BUSY,
        )

        val event = reader.toIcsEvent(
            reminderMinutes = listOf(10),
            calendarName = "Personal",
            allDayReminderTimeMinutes = NINE_AM,
        )

        assertThat(event.uid).isEqualTo("abc@host")
        assertThat(event.summary).isEqualTo("Standup")
        assertThat(event.start.toEpochMilliseconds()).isEqualTo(1_000_000L)
        assertThat(event.end.toEpochMilliseconds()).isEqualTo(1_900_000L)
        assertThat(event.isAllDay).isFalse()
        assertThat(event.recurrenceRule).isNull()
        assertThat(event.reminderMinutes).containsExactly(10)
        assertThat(event.calendarName).isEqualTo("Personal")
        assertThat(event.status).isEqualTo(EventStatus.Confirmed)
    }

    @Test
    fun `recurring row without DTEND reconstructs end from DURATION`() {
        val reader = MapColumnReader(
            EventExportProjection.IDX_ID to 7L,
            // No UID column → synthesised stably from id + dtstart.
            EventExportProjection.IDX_TITLE to "Weekly",
            EventExportProjection.IDX_DTSTART to 1_000_000L,
            // DTEND absent (null); DURATION carries the length.
            EventExportProjection.IDX_DURATION to "P3600S",
            EventExportProjection.IDX_ALL_DAY to 0,
            EventExportProjection.IDX_RRULE to "FREQ=WEEKLY",
            EventExportProjection.IDX_EVENT_TIMEZONE to "UTC",
        )

        val event = reader.toIcsEvent(
            reminderMinutes = emptyList(),
            calendarName = null,
            allDayReminderTimeMinutes = NINE_AM,
        )

        assertThat(event.uid).isEqualTo("7-1000000@calendula")
        assertThat(event.recurrenceRule).isEqualTo("FREQ=WEEKLY")
        assertThat(event.end.toEpochMilliseconds()).isEqualTo(1_000_000L + 3_600_000L)
    }

    @Test
    fun `all-day flag is carried through`() {
        val reader = MapColumnReader(
            EventExportProjection.IDX_ID to 1L,
            EventExportProjection.IDX_TITLE to "Holiday",
            EventExportProjection.IDX_DTSTART to 0L,
            EventExportProjection.IDX_DTEND to 86_400_000L,
            EventExportProjection.IDX_ALL_DAY to 1,
            EventExportProjection.IDX_EVENT_TIMEZONE to "UTC",
        )

        assertThat(reader.toIcsEvent(emptyList(), null, NINE_AM).isAllDay).isTrue()
    }

    @Test
    fun `an all-day reminder is exported as its whole-day lead time`() {
        // The raw offset encodes the firing time and is normally negative; left
        // raw it would be written as a trigger after the event and dropped, so
        // the backup would come back with no reminder at all.
        val reader = MapColumnReader(
            EventExportProjection.IDX_ID to 1L,
            EventExportProjection.IDX_TITLE to "Anna Birthday",
            EventExportProjection.IDX_DTSTART to 1_782_864_000_000L, // 2026-07-01 UTC, CEST
            EventExportProjection.IDX_DTEND to 1_782_950_400_000L,
            EventExportProjection.IDX_ALL_DAY to 1,
            EventExportProjection.IDX_EVENT_TIMEZONE to "UTC",
        )

        val onTheDay = reader.toIcsEvent(listOf(-420), null, NINE_AM, BERLIN)
        val dayBefore = reader.toIcsEvent(listOf(1_020), null, NINE_AM, BERLIN)

        assertThat(onTheDay.reminderMinutes).containsExactly(0)
        assertThat(dayBefore.reminderMinutes).containsExactly(1_440)
    }

    private companion object {
        const val NINE_AM = 9 * 60
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
    }
}
