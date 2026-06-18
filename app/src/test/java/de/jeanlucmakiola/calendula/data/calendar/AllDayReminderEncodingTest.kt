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
                    assertThat(fromProviderAllDayMinutes(raw, date, berlin)).isEqualTo(semantic)
                }
            }
        }
    }

    @Test
    fun `pre-feature rows (raw multiple of 1440) still decode to whole days`() {
        // Reminders written before this feature stored raw N*1440 (fired at UTC
        // midnight). They must still read back as "N days before".
        assertThat(fromProviderAllDayMinutes(1_440, summer, berlin)).isEqualTo(1_440)
        assertThat(fromProviderAllDayMinutes(1_440, winter, berlin)).isEqualTo(1_440)
        assertThat(fromProviderAllDayMinutes(2_880, summer, berlin)).isEqualTo(2_880)
    }

    @Test
    fun `decoding is independent of the time-of-day used to encode`() {
        val atNine = toProviderAllDayMinutes(1_440, summer, berlin, nineAm)
        val atEight = toProviderAllDayMinutes(1_440, summer, berlin, 8 * 60)
        assertThat(atNine).isNotEqualTo(atEight)
        assertThat(fromProviderAllDayMinutes(atNine, summer, berlin)).isEqualTo(1_440)
        assertThat(fromProviderAllDayMinutes(atEight, summer, berlin)).isEqualTo(1_440)
    }

    @Test
    fun `negative semantic minutes (provider-default sentinel) pass through`() {
        assertThat(toProviderAllDayMinutes(-1, summer, berlin, nineAm)).isEqualTo(-1)
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
