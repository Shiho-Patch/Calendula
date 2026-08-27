package de.jeanlucmakiola.calendula.widget

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The rollover alarm is what makes the widgets stop highlighting yesterday
 * (#228), so the "when is the next local midnight" arithmetic is the one piece
 * worth pinning down — especially where midnight is not 00:00.
 */
class WidgetRolloverSchedulerTest {

    private val berlin = TimeZone.of("Europe/Berlin")

    private fun at(local: String, zone: TimeZone): Instant =
        LocalDateTime.parse(local).toInstant(zone)

    private fun nextRollover(local: String, zone: TimeZone = berlin): Instant =
        WidgetRolloverScheduler.nextRolloverAt(at(local, zone), zone)

    // --- the ordinary day ----------------------------------------------------

    @Test
    fun `midday rolls over at the coming midnight`() {
        val next = nextRollover("2026-08-27T12:00:00")
        assertThat(next).isEqualTo(at("2026-08-28T00:00:05", berlin))
    }

    @Test
    fun `a second before midnight still targets tonight, not tomorrow night`() {
        val next = nextRollover("2026-08-27T23:59:59")
        assertThat(next).isEqualTo(at("2026-08-28T00:00:05", berlin))
    }

    @Test
    fun `at midnight exactly the target is the next day, never the current instant`() {
        // The alarm has just fired and is re-arming: it must move a whole day on,
        // otherwise the widget would wake itself in a tight loop.
        val now = at("2026-08-28T00:00:00", berlin)
        val next = WidgetRolloverScheduler.nextRolloverAt(now, berlin)
        assertThat(next).isEqualTo(at("2026-08-29T00:00:05", berlin))
        assertThat(next - now).isGreaterThan(Duration.ZERO)
    }

    @Test
    fun `re-arming from the slack instant itself moves a full day on`() {
        // What actually happens in practice: the receiver runs at midnight + slack.
        val now = at("2026-08-28T00:00:05", berlin)
        assertThat(WidgetRolloverScheduler.nextRolloverAt(now, berlin))
            .isEqualTo(at("2026-08-29T00:00:05", berlin))
    }

    @Test
    fun `the result is always in the future for every minute of a day`() {
        val zone = berlin
        var probe = LocalDateTime.parse("2026-03-28T00:00:00").toInstant(zone)
        val end = LocalDateTime.parse("2026-03-31T00:00:00").toInstant(zone)
        while (probe < end) {
            assertThat(WidgetRolloverScheduler.nextRolloverAt(probe, zone)).isGreaterThan(probe)
            probe += 1.minutes
        }
    }

    // --- daylight saving -----------------------------------------------------

    @Test
    fun `spring forward keeps the rollover one day away, not one hour short`() {
        // Berlin skips 02:00-03:00 on 29 March 2026, so that day is 23h long.
        val now = at("2026-03-28T12:00:00", berlin)
        val next = WidgetRolloverScheduler.nextRolloverAt(now, berlin)
        assertThat(next).isEqualTo(at("2026-03-29T00:00:05", berlin))
        assertThat(next.toLocalDateTime(berlin).hour).isEqualTo(0)
    }

    @Test
    fun `fall back lands on the first of the two midnights`() {
        // Berlin repeats 02:00-03:00 on 25 October 2026; midnight itself is
        // unambiguous, but the day is 25h long.
        val next = nextRollover("2026-10-24T12:00:00")
        assertThat(next.toLocalDateTime(berlin).hour).isEqualTo(0)
        assertThat(next).isEqualTo(
            LocalDate.parse("2026-10-25").atStartOfDayIn(berlin) +
                WidgetRolloverScheduler.ROLLOVER_SLACK,
        )
    }

    @Test
    fun `a zone where midnight does not exist rolls over at the real start of day`() {
        // Cuba starts DST at 00:00, so 8 March 2026 begins at 01:00 in Havana.
        // Targeting a literal 00:00 there would arm an instant on the wrong day.
        val havana = TimeZone.of("America/Havana")
        val next = WidgetRolloverScheduler.nextRolloverAt(at("2026-03-07T12:00:00", havana), havana)
        val local = next.toLocalDateTime(havana)
        assertThat(local.date.toString()).isEqualTo("2026-03-08")
        assertThat(local.hour).isEqualTo(1)
        assertThat(local.minute).isEqualTo(0)
    }

    // --- timezone changes ----------------------------------------------------

    @Test
    fun `the same instant rolls over at different times in different zones`() {
        // Flying east and getting TIMEZONE_CHANGED must re-arm to the new local
        // midnight — the arithmetic follows the zone, not a cached offset.
        val instant = at("2026-08-27T12:00:00", berlin)
        val tokyo = TimeZone.of("Asia/Tokyo")
        val berlinNext = WidgetRolloverScheduler.nextRolloverAt(instant, berlin)
        val tokyoNext = WidgetRolloverScheduler.nextRolloverAt(instant, tokyo)
        assertThat(tokyoNext).isNotEqualTo(berlinNext)
        assertThat(tokyoNext).isLessThan(berlinNext)
        assertThat(tokyoNext.toLocalDateTime(tokyo).hour).isEqualTo(0)
    }

    @Test
    fun `a half-hour offset zone still lands on its own midnight`() {
        val kathmandu = TimeZone.of("Asia/Kathmandu")
        val next = WidgetRolloverScheduler.nextRolloverAt(
            at("2026-08-27T12:00:00", kathmandu), kathmandu,
        )
        val local = next.toLocalDateTime(kathmandu)
        assertThat(local.date.toString()).isEqualTo("2026-08-28")
        assertThat(local.hour).isEqualTo(0)
    }

    @Test
    fun `the slack is small enough to be invisible and large enough to clear the boundary`() {
        // Guards the constant: too small and a slightly early alarm still reads
        // yesterday's date; minutes of it and the widget visibly lags midnight.
        assertThat(WidgetRolloverScheduler.ROLLOVER_SLACK.inWholeSeconds).isAtLeast(1)
        assertThat(WidgetRolloverScheduler.ROLLOVER_SLACK.inWholeSeconds).isAtMost(60)
    }
}
