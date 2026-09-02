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

    @Test
    fun `a recurring row carries its EXDATE exclusions`() {
        val reader = MapColumnReader(
            EventExportProjection.IDX_ID to 7L,
            EventExportProjection.IDX_TITLE to "Weekly",
            EventExportProjection.IDX_DTSTART to 1_000_000L,
            EventExportProjection.IDX_DURATION to "P3600S",
            EventExportProjection.IDX_ALL_DAY to 0,
            EventExportProjection.IDX_RRULE to "FREQ=WEEKLY",
            EventExportProjection.IDX_EXDATE to "20260901T080000Z,20260908T080000Z",
            EventExportProjection.IDX_EVENT_TIMEZONE to "UTC",
        )

        val event = reader.toIcsEvent(
            reminderMinutes = emptyList(),
            calendarName = null,
            allDayReminderTimeMinutes = NINE_AM,
        )

        assertThat(event.exDates)
            .containsExactly("20260901T080000Z", "20260908T080000Z").inOrder()
    }

    @Test
    fun `an all-day EXDATE keeps only the day, however the adapter padded it`() {
        val reader = MapColumnReader(
            EventExportProjection.IDX_ID to 8L,
            EventExportProjection.IDX_TITLE to "Holiday",
            EventExportProjection.IDX_DTSTART to 0L,
            EventExportProjection.IDX_DURATION to "P1D",
            EventExportProjection.IDX_ALL_DAY to 1,
            EventExportProjection.IDX_RRULE to "FREQ=YEARLY",
            EventExportProjection.IDX_EXDATE to "20260901,20270901T000000Z",
            EventExportProjection.IDX_EVENT_TIMEZONE to "UTC",
        )

        val event = reader.toIcsEvent(
            reminderMinutes = emptyList(),
            calendarName = null,
            allDayReminderTimeMinutes = NINE_AM,
        )

        assertThat(event.exDates).containsExactly("20260901", "20270901").inOrder()
    }

    @Test
    fun `a TZID-prefixed EXDATE is resolved to UTC, not handed on verbatim`() {
        // The provider stores a zoned exclusion as AOSP RecurrenceSet does. Passed
        // through, the writer would emit `EXDATE:TZID=Europe/Berlin;2026...` — the
        // parameter belongs before the colon, so the line is unreadable.
        val reader = MapColumnReader(
            EventExportProjection.IDX_ID to 11L,
            EventExportProjection.IDX_TITLE to "Weekly",
            EventExportProjection.IDX_DTSTART to 1_000_000L,
            EventExportProjection.IDX_DURATION to "P3600S",
            EventExportProjection.IDX_ALL_DAY to 0,
            EventExportProjection.IDX_RRULE to "FREQ=WEEKLY",
            EventExportProjection.IDX_EXDATE to
                "TZID=Europe/Berlin;20260722T090000,20260729T090000",
            EventExportProjection.IDX_EVENT_TIMEZONE to "Europe/Berlin",
        )

        val event = reader.toIcsEvent(
            reminderMinutes = emptyList(),
            calendarName = null,
            allDayReminderTimeMinutes = NINE_AM,
        )

        // 09:00 Berlin in July is CEST (+2) == 07:00Z.
        assertThat(event.exDates)
            .containsExactly("20260722T070000Z", "20260729T070000Z").inOrder()
    }

    @Test
    fun `a zoned EXDATE in the provider's own bare form resolves in that zone`() {
        // AOSP's RecurrenceSet stores the TZID parameter's value alone. Read as
        // if it were unzoned, 09:00 Berlin exported as 09:00Z — two hours out,
        // so the exclusion no longer matched its occurrence in the file.
        val reader = MapColumnReader(
            EventExportProjection.IDX_ID to 14L,
            EventExportProjection.IDX_TITLE to "Weekly",
            EventExportProjection.IDX_DTSTART to 1_000_000L,
            EventExportProjection.IDX_DURATION to "P3600S",
            EventExportProjection.IDX_ALL_DAY to 0,
            EventExportProjection.IDX_RRULE to "FREQ=WEEKLY",
            EventExportProjection.IDX_EXDATE to "Europe/Berlin;20260722T090000",
            EventExportProjection.IDX_EVENT_TIMEZONE to "UTC",
        )

        val event = reader.toIcsEvent(
            reminderMinutes = emptyList(),
            calendarName = null,
            allDayReminderTimeMinutes = NINE_AM,
        )

        assertThat(event.exDates).containsExactly("20260722T070000Z")
    }

    @Test
    fun `a TZID this device cannot place falls back to the row's own zone`() {
        // Exchange spells zones its own way, so ZoneId.of refuses the name.
        // Stripping the prefix and emitting the stamp as it stands would export a
        // floating time against a `DTSTART;TZID=…` line — every reader in another
        // zone resolves it to another instant and the exclusion stops matching.
        val reader = MapColumnReader(
            EventExportProjection.IDX_ID to 13L,
            EventExportProjection.IDX_TITLE to "Weekly",
            EventExportProjection.IDX_DTSTART to 1_000_000L,
            EventExportProjection.IDX_DURATION to "P3600S",
            EventExportProjection.IDX_ALL_DAY to 0,
            EventExportProjection.IDX_RRULE to "FREQ=WEEKLY",
            EventExportProjection.IDX_EXDATE to
                "TZID=W. Europe Standard Time;20260722T090000",
            EventExportProjection.IDX_EVENT_TIMEZONE to "Europe/Berlin",
        )

        val event = reader.toIcsEvent(
            reminderMinutes = emptyList(),
            calendarName = null,
            allDayReminderTimeMinutes = NINE_AM,
        )

        assertThat(event.exDates).containsExactly("20260722T070000Z")
    }

    @Test
    fun `an all-day EXDATE loses a TZID prefix along with its time`() {
        val reader = MapColumnReader(
            EventExportProjection.IDX_ID to 12L,
            EventExportProjection.IDX_TITLE to "Holiday",
            EventExportProjection.IDX_DTSTART to 0L,
            EventExportProjection.IDX_DURATION to "P1D",
            EventExportProjection.IDX_ALL_DAY to 1,
            EventExportProjection.IDX_RRULE to "FREQ=YEARLY",
            EventExportProjection.IDX_EXDATE to "TZID=Europe/Berlin;20260901T000000",
            EventExportProjection.IDX_EVENT_TIMEZONE to "Europe/Berlin",
        )

        val event = reader.toIcsEvent(
            reminderMinutes = emptyList(),
            calendarName = null,
            allDayReminderTimeMinutes = NINE_AM,
        )

        assertThat(event.exDates).containsExactly("20260901")
    }

    @Test
    fun `a default-lead all-day reminder is not decoded into an at-start alarm`() {
        // MINUTES_DEFAULT (-1) says "use the account default", not "0 minutes
        // before". Decoded, it landed on the start date and became a real alarm
        // that IcsWriter no longer dropped.
        val reader = MapColumnReader(
            EventExportProjection.IDX_ID to 13L,
            EventExportProjection.IDX_TITLE to "Birthday",
            EventExportProjection.IDX_DTSTART to 0L,
            EventExportProjection.IDX_DURATION to "P1D",
            EventExportProjection.IDX_ALL_DAY to 1,
            EventExportProjection.IDX_EVENT_TIMEZONE to "UTC",
        )

        val event = reader.toIcsEvent(
            reminderMinutes = listOf(-1),
            calendarName = null,
            allDayReminderTimeMinutes = NINE_AM,
            zone = BERLIN,
        )

        assertThat(event.reminderMinutes).containsExactly(-1)
    }

    @Test
    fun `a one-off row exports no exclusions even if the column is set`() {
        val reader = MapColumnReader(
            EventExportProjection.IDX_ID to 9L,
            EventExportProjection.IDX_TITLE to "Standup",
            EventExportProjection.IDX_DTSTART to 1_000_000L,
            EventExportProjection.IDX_DTEND to 1_900_000L,
            EventExportProjection.IDX_ALL_DAY to 0,
            EventExportProjection.IDX_EXDATE to "20260901T080000Z",
            EventExportProjection.IDX_EVENT_TIMEZONE to "UTC",
        )

        val event = reader.toIcsEvent(
            reminderMinutes = emptyList(),
            calendarName = null,
            allDayReminderTimeMinutes = NINE_AM,
        )

        assertThat(event.exDates).isEmpty()
    }
}
