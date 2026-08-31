package de.jeanlucmakiola.calendula.data.calendar

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class AllDayReminderEncodingTest {

    private val berlin: ZoneId = ZoneId.of("Europe/Berlin")
    private val nineAm = 9 * 60
    private val summer = LocalDate.of(2026, 6, 20) // CEST, UTC+2
    private val winter = LocalDate.of(2026, 1, 20) // CET, UTC+1

    /** The instant the provider would actually fire: DTSTART(UTC midnight) − raw. */
    private fun actualFire(rawMinutes: Int, startDate: LocalDate): Long =
        startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() -
            rawMinutes * 60_000L

    /** The wall-clock instant we intend: [time] local, [daysBefore] days before [startDate]. */
    private fun intendedFire(startDate: LocalDate, daysBefore: Int, timeMinutes: Int): Long =
        startDate.minusDays(daysBefore.toLong())
            .atTime(LocalTime.of(timeMinutes / 60, timeMinutes % 60))
            .atZone(berlin).toInstant().toEpochMilli()

    @Test
    fun `one day before at 9am fires at 9am local the day before (summer)`() {
        val raw = toProviderAllDayMinutes(1_440, summer, berlin, nineAm)
        assertThat(actualFire(raw, summer)).isEqualTo(intendedFire(summer, 1, nineAm))
        // 09:00 CEST is 07:00Z, 7h later than the bare midnight offset: 1440 − 420.
        assertThat(raw).isEqualTo(1_020)
    }

    @Test
    fun `one day before at 9am fires at 9am local the day before (winter)`() {
        val raw = toProviderAllDayMinutes(1_440, winter, berlin, nineAm)
        assertThat(actualFire(raw, winter)).isEqualTo(intendedFire(winter, 1, nineAm))
        // 09:00 CET is 08:00Z, 8h later than midnight: 1440 − 480.
        assertThat(raw).isEqualTo(960)
    }

    @Test
    fun `at time of event encodes a negative offset firing 9am on the day (summer)`() {
        val raw = toProviderAllDayMinutes(0, summer, berlin, nineAm)
        assertThat(raw).isLessThan(0) // fires after DTSTART; must not be clamped
        assertThat(actualFire(raw, summer)).isEqualTo(intendedFire(summer, 0, nineAm))
    }

    @Test
    fun `round-trips for whole-day lead times across both seasons`() {
        for (date in listOf(summer, winter)) {
            for (time in listOf(0, nineAm, 20 * 60)) {
                for (semantic in listOf(0, 1_440, 2_880, 10_080)) {
                    val raw = toProviderAllDayMinutes(semantic, date, berlin, time)
                    assertThat(fromProviderAllDayMinutes(raw, date, berlin, time))
                        .isEqualTo(semantic)
                }
            }
        }
    }

    @Test
    fun `pre-feature rows (raw multiple of 1440) still decode to whole days`() {
        // Reminders written before this feature stored raw N*1440 (fired at UTC
        // midnight). They must still read back as "N days before".
        assertThat(fromProviderAllDayMinutes(1_440, summer, berlin, nineAm)).isEqualTo(1_440)
        assertThat(fromProviderAllDayMinutes(1_440, winter, berlin, nineAm)).isEqualTo(1_440)
        assertThat(fromProviderAllDayMinutes(2_880, summer, berlin, nineAm)).isEqualTo(2_880)
    }

    @Test
    fun `an offset that encodes to a multiple still decodes to its own lead time`() {
        // New York at 20:00 is the collision: "1 day before" encodes to a plain 0,
        // which read at face value would say "at time of event". The screen has to
        // show what the reminder does, which is fire the evening before.
        val newYork = ZoneId.of("America/New_York")
        val eightPm = 20 * 60
        val raw = toProviderAllDayMinutes(1_440, summer, newYork, eightPm)

        assertThat(raw).isEqualTo(0)
        assertThat(fromProviderAllDayMinutes(raw, summer, newYork, eightPm)).isEqualTo(1_440)
    }

    @Test
    fun `a foreign multiple west of UTC keeps its face-value lead time`() {
        // No encoded hour in this row, and its instant lands on the evening two
        // days out in New York — the local-date reading would say "2 days before".
        val newYork = ZoneId.of("America/New_York")

        assertThat(fromProviderAllDayMinutes(1_440, summer, newYork, nineAm)).isEqualTo(1_440)
    }

    @Test
    fun `decoding is independent of the time-of-day used to encode`() {
        val atNine = toProviderAllDayMinutes(1_440, summer, berlin, nineAm)
        val atEight = toProviderAllDayMinutes(1_440, summer, berlin, 8 * 60)
        assertThat(atNine).isNotEqualTo(atEight)
        assertThat(fromProviderAllDayMinutes(atNine, summer, berlin, nineAm)).isEqualTo(1_440)
        assertThat(fromProviderAllDayMinutes(atEight, summer, berlin, nineAm)).isEqualTo(1_440)
    }

    @Test
    fun `negative semantic minutes (provider-default sentinel) pass through`() {
        assertThat(toProviderAllDayMinutes(-1, summer, berlin, nineAm)).isEqualTo(-1)
    }

    @Test
    fun `next yearly occurrence wraps to next year once this year's date has passed`() {
        val today = LocalDate.of(2026, 8, 1)
        assertThat(nextYearlyOccurrence(5, 14, today)).isEqualTo(LocalDate.of(2027, 5, 14))
        assertThat(nextYearlyOccurrence(12, 25, today)).isEqualTo(LocalDate.of(2026, 12, 25))
    }

    @Test
    fun `next yearly occurrence returns today when the date is today`() {
        val today = LocalDate.of(2026, 3, 10)
        assertThat(nextYearlyOccurrence(3, 10, today)).isEqualTo(today)
    }

    @Test
    fun `next yearly occurrence skips non-leap years for a Feb-29 date`() {
        assertThat(nextYearlyOccurrence(2, 29, LocalDate.of(2026, 6, 1)))
            .isEqualTo(LocalDate.of(2028, 2, 29))
    }

    @Test
    fun `sampling the offset at the next occurrence, not a 1972 anchor, fixes the fire hour`() {
        // A year-less birthday's DTSTART anchor is 1972 (Berlin had no DST then,
        // UTC+1). Sampling the offset there skews a modern CEST occurrence by an
        // hour; sampling at the upcoming occurrence fires at the intended time.
        val anchor1972 = LocalDate.of(1972, 7, 15)
        val nextOccurrence = nextYearlyOccurrence(7, 15, LocalDate.of(2026, 6, 1)) // 2026-07-15, CEST
        val rawFromAnchor = toProviderAllDayMinutes(0, anchor1972, berlin, nineAm)
        val rawFromNext = toProviderAllDayMinutes(0, nextOccurrence, berlin, nineAm)

        val fireFromAnchor = actualFire(rawFromAnchor, nextOccurrence)
            .let(java.time.Instant::ofEpochMilli).atZone(berlin).toLocalTime()
        val fireFromNext = actualFire(rawFromNext, nextOccurrence)
            .let(java.time.Instant::ofEpochMilli).atZone(berlin).toLocalTime()
        assertThat(fireFromAnchor).isEqualTo(LocalTime.of(10, 0)) // skewed +1h
        assertThat(fireFromNext).isEqualTo(LocalTime.of(9, 0)) // correct
    }

    @Test
    fun `an imported yearly series samples at its next occurrence, not its anchor`() {
        // Fossify anchors a year-less contact birthday at 1970, when Berlin had
        // no DST; sampling there fires a modern June occurrence at 10:00 instead
        // of the 09:00 the user picked (Codeberg #225).
        val anchor = LocalDate.of(1970, 6, 3)
        val today = LocalDate.of(2026, 6, 1)

        val sampled = importedAllDayReminderDate(anchor, "FREQ=YEARLY;BYMONTH=6", today)
        assertThat(sampled).isEqualTo(LocalDate.of(2026, 6, 3))

        val raw = toProviderAllDayMinutes(0, sampled, berlin, nineAm)
        val fire = actualFire(raw, LocalDate.of(2027, 6, 3))
            .let(java.time.Instant::ofEpochMilli).atZone(berlin).toLocalTime()
        assertThat(fire).isEqualTo(LocalTime.of(9, 0))
    }

    @Test
    fun `the lead time still reads back from the anchor's own date`() {
        // The offset is sampled at the next occurrence but decoded against
        // DTSTART, so the two must still agree on the whole-day lead time.
        val anchor = LocalDate.of(1970, 6, 3)
        val sampled = importedAllDayReminderDate(anchor, "FREQ=YEARLY", LocalDate.of(2026, 6, 1))

        for (semantic in listOf(0, 1_440, 2_880)) {
            val raw = toProviderAllDayMinutes(semantic, sampled, berlin, nineAm)
            assertThat(fromProviderAllDayMinutes(raw, anchor, berlin, nineAm)).isEqualTo(semantic)
        }
    }

    @Test
    fun `a one-off import samples at its own date, a stale series at today`() {
        val past = LocalDate.of(1970, 6, 3)
        val today = LocalDate.of(2026, 6, 1)
        val future = LocalDate.of(2027, 3, 4)

        assertThat(importedAllDayReminderDate(past, null, today)).isEqualTo(past)
        assertThat(importedAllDayReminderDate(past, "FREQ=WEEKLY;BYDAY=MO", today)).isEqualTo(today)
        assertThat(importedAllDayReminderDate(future, "FREQ=WEEKLY", today)).isEqualTo(future)
    }

    @Test
    fun `a winter-anchored offset drifts one hour on a summer occurrence`() {
        // Known limitation: one fixed MINUTES per series can't track DST. An
        // offset tuned for a CET anchor fires an hour off once the series crosses
        // into CEST. Bounded to ±1h; documented, not fixed.
        val raw = toProviderAllDayMinutes(1_440, winter, berlin, nineAm)
        val summerOccurrence = LocalDate.of(2026, 7, 20)
        val fire = actualFire(raw, summerOccurrence).let(java.time.Instant::ofEpochMilli)
            .atZone(berlin).toLocalTime()
        assertThat(fire).isEqualTo(LocalTime.of(10, 0)) // 09:00 intended, +1h in CEST
    }
}
