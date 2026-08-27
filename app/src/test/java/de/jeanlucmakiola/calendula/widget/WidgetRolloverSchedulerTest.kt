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
import kotlin.time.Duration.Companion.hours
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
        // Spans Berlin's 2024 spring-forward, the case most likely to produce a
        // target in the past and so an alarm that fires immediately, forever.
        var probe = LocalDateTime.parse("2024-03-29T00:00:00").toInstant(zone)
        val end = LocalDateTime.parse("2024-04-01T00:00:00").toInstant(zone)
        while (probe < end) {
            assertThat(WidgetRolloverScheduler.nextRolloverAt(probe, zone)).isGreaterThan(probe)
            probe += 1.minutes
        }
    }

    // --- daylight saving -----------------------------------------------------

    @Test
    fun `spring forward keeps the rollover one day away, not one hour short`() {
        // Berlin skipped 02:00-03:00 on 31 March 2024, so that day was 23h long.
        // A rollover computed as "now + 24h" would land at 01:00 on 1 April.
        val now = at("2024-03-30T12:00:00", berlin)
        val next = WidgetRolloverScheduler.nextRolloverAt(now, berlin)
        assertThat(next).isEqualTo(at("2024-03-31T00:00:05", berlin))
        assertThat(next - now).isLessThan(24.hours)
    }

    @Test
    fun `fall back does not overshoot into the repeated hour`() {
        // Berlin repeated 02:00-03:00 on 27 October 2024: midnight itself is
        // unambiguous, but the day is 25h long, so "now + 24h" would land at
        // 23:00 on the 26th and never roll the date over at all.
        val now = at("2024-10-26T12:00:00", berlin)
        val next = WidgetRolloverScheduler.nextRolloverAt(now, berlin)
        assertThat(next).isEqualTo(at("2024-10-27T00:00:05", berlin))
        assertThat(next.toLocalDateTime(berlin).date).isEqualTo(LocalDate.parse("2024-10-27"))
    }

    @Test
    fun `a zone that repeats midnight takes the first start of day`() {
        // Sao Paulo used to end DST by moving 00:00 back to 23:00, so the day
        // began twice. Pinned deliberately: the widget then runs an hour ahead
        // of the clock until the next redraw, which is the accepted trade
        // (Brazil dropped DST in 2019, so no live zone does this).
        val saoPaulo = TimeZone.of("America/Sao_Paulo")
        val next = WidgetRolloverScheduler.nextRolloverAt(
            at("2018-02-16T12:00:00", saoPaulo), saoPaulo,
        )
        val local = next.toLocalDateTime(saoPaulo)
        assertThat(local.date).isEqualTo(LocalDate.parse("2018-02-17"))
        assertThat(local.hour).isEqualTo(0)
    }

    @Test
    fun `a zone where midnight does not exist rolls over at the real start of day`() {
        // Cuba starts DST at 00:00, so 11 March 2018 began at 01:00 in Havana.
        // Targeting a literal 00:00 there would arm an instant on the wrong day.
        val havana = TimeZone.of("America/Havana")
        val next = WidgetRolloverScheduler.nextRolloverAt(at("2018-03-10T12:00:00", havana), havana)
        val local = next.toLocalDateTime(havana)
        assertThat(local.date).isEqualTo(LocalDate.parse("2018-03-11"))
        assertThat(local.hour).isEqualTo(1)
        assertThat(local.minute).isEqualTo(0)
        // And it is genuinely the first instant of that date, not a guess.
        assertThat(next).isEqualTo(
            LocalDate.parse("2018-03-11").atStartOfDayIn(havana) +
                WidgetRolloverScheduler.ROLLOVER_SLACK,
        )
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

    // --- the wiring ----------------------------------------------------------

    @Test
    fun `the receiver actually handles the action the alarm is sent with`() {
        // The single point where the whole fix would die silently: the alarm
        // fires, the receiver drops it on the action guard, nothing redraws.
        assertThat(WidgetUpdateReceiver.HANDLED_ACTIONS)
            .contains(WidgetUpdateReceiver.ACTION_ROLLOVER)
    }
}
