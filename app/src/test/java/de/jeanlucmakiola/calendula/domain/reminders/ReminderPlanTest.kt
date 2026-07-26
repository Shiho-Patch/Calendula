package de.jeanlucmakiola.calendula.domain.reminders

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * The decision layer of in-house reminder delivery (#75). The watermark rules are
 * the load-bearing part: they are what makes a dropped alarm recoverable and a
 * double scan harmless, now that no provider row records "already fired".
 */
class ReminderPlanTest {

    private val now = 1_700_000_000_000L
    private val minute = 60_000L
    private val day = 24 * 60 * minute

    private fun instance(
        eventId: Long,
        beginMillis: Long = now + 30 * minute,
        endMillis: Long = now + 90 * minute,
        calendarId: Long = 7L,
        isAllDay: Boolean = false,
    ) = ReminderEventInstance(
        eventId = eventId,
        calendarId = calendarId,
        beginMillis = beginMillis,
        endMillis = endMillis,
        title = "Event $eventId",
        location = null,
        isAllDay = isAllDay,
    )

    /** [planReminders] with the fixtures' zone and all-day hour filled in. */
    private fun plan(
        instances: List<ReminderEventInstance>,
        minutesByEvent: Map<Long, List<Int>>,
        zone: ZoneId = berlin,
        allDayTimeMinutes: Int = nineAm,
    ) = planReminders(instances, minutesByEvent, zone, allDayTimeMinutes)

    @Test
    fun `a reminder fires its offset before the occurrence begins`() {
        val begin = now + 30 * minute
        val planned = plan(
            instances = listOf(instance(1L, beginMillis = begin)),
            minutesByEvent = mapOf(1L to listOf(10)),
        )

        assertThat(planned.map { it.alarmMillis }).containsExactly(begin - 10 * minute)
    }

    // --- all-day: the hour the user picked, on every occurrence -------------

    private val berlin = ZoneId.of("Europe/Berlin")
    private val nineAm = 540

    /** UTC midnight of [date] — how the provider stores an all-day occurrence. */
    private fun allDayBegin(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun firedAt(alarmMillis: Long, zone: ZoneId = berlin): ZonedDateTime =
        java.time.Instant.ofEpochMilli(alarmMillis).atZone(zone)

    /**
     * The offset AllDayReminderEncoding would store for "[days] before, at
     * [timeOfDayMinutes]" when sampled against [eventDate] — i.e. exactly the row
     * the app writes today.
     */
    private fun encodedAllDayMinutes(
        eventDate: String,
        days: Long,
        timeOfDayMinutes: Int = nineAm,
        zone: ZoneId = berlin,
    ): Int {
        val date = LocalDate.parse(eventDate)
        val utcMidnight = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val fire = date.minusDays(days)
            .atTime(LocalTime.of(timeOfDayMinutes / 60, timeOfDayMinutes % 60))
            .atZone(zone).toInstant().toEpochMilli()
        return ((utcMidnight - fire) / minute).toInt()
    }

    private fun allDayAlarm(eventDate: String, rawMinutes: Int, zone: ZoneId = berlin): Long =
        plan(
            instances = listOf(
                instance(1L, beginMillis = allDayBegin(eventDate), isAllDay = true),
            ),
            minutesByEvent = mapOf(1L to listOf(rawMinutes)),
            zone = zone,
            allDayTimeMinutes = nineAm,
        ).single().alarmMillis

    @Test
    fun `an all-day reminder fires at the hour the setting names`() {
        // Winter: Berlin is UTC+1. "1 day before" on the 15th means the 14th, 09:00.
        val alarm = allDayAlarm("2026-01-15", encodedAllDayMinutes("2026-01-15", days = 1))

        assertThat(firedAt(alarm).toLocalDateTime())
            .isEqualTo(LocalDate.parse("2026-01-14").atTime(9, 0))
    }

    @Test
    fun `a summer occurrence of a row written in winter is not an hour early`() {
        // The drift this replaces: the offset was sampled at UTC+1, the occurrence
        // falls at UTC+2, and firing at `begin - minutes` would land at 08:00.
        val winterRow = encodedAllDayMinutes("2026-01-15", days = 1)

        val alarm = allDayAlarm("2026-07-15", winterRow)

        assertThat(firedAt(alarm).toLocalDateTime())
            .isEqualTo(LocalDate.parse("2026-07-14").atTime(9, 0))
    }

    @Test
    fun `a winter occurrence of a row written in summer is not an hour late`() {
        // The same drift in the other direction: sampled at UTC+2, fires at UTC+1.
        val summerRow = encodedAllDayMinutes("2026-07-15", days = 1)

        val alarm = allDayAlarm("2026-01-15", summerRow)

        assertThat(firedAt(alarm).toLocalDateTime())
            .isEqualTo(LocalDate.parse("2026-01-14").atTime(9, 0))
    }

    @Test
    fun `every occurrence of a yearly all-day series fires at the same wall clock`() {
        // A birthday's offset is sampled once; the series must not walk off it.
        val row = encodedAllDayMinutes("2026-07-15", days = 1)
        val fired = listOf("2026-07-15", "2027-01-15", "2027-07-15", "2028-01-15")
            .map { firedAt(allDayAlarm(it, row)).toLocalTime() }

        assertThat(fired.toSet()).containsExactly(LocalTime.of(9, 0))
    }

    @Test
    fun `an all-day reminder on the day itself fires that morning`() {
        // "At time of event" on an all-day event encodes to a negative offset.
        val sameDay = encodedAllDayMinutes("2026-07-15", days = 0)
        assertThat(sameDay).isLessThan(0)

        val alarm = allDayAlarm("2026-07-15", sameDay)

        assertThat(firedAt(alarm).toLocalDateTime())
            .isEqualTo(LocalDate.parse("2026-07-15").atTime(9, 0))
    }

    @Test
    fun `a plain 1440 row from another calendar app means one day before`() {
        // Foreign rows carry no encoded hour. Read at face value they would fire
        // at UTC midnight — 02:00 local in summer Berlin.
        val alarm = allDayAlarm("2026-07-15", rawMinutes = 1_440)

        assertThat(firedAt(alarm).toLocalDateTime())
            .isEqualTo(LocalDate.parse("2026-07-14").atTime(9, 0))
    }

    @Test
    fun `a plain 1440 row west of UTC still means one day before`() {
        // UTC midnight of the 15th is the evening of the 14th in New York, so a
        // local-date reading of the offset would put this two days out.
        val newYork = ZoneId.of("America/New_York")

        val alarm = allDayAlarm("2026-07-15", rawMinutes = 1_440, zone = newYork)

        assertThat(firedAt(alarm, newYork).toLocalDateTime())
            .isEqualTo(LocalDate.parse("2026-07-14").atTime(9, 0))
    }

    @Test
    fun `an encoded row west of UTC fires at the named hour`() {
        val newYork = ZoneId.of("America/New_York")
        val row = encodedAllDayMinutes("2026-07-15", days = 1, zone = newYork)

        val alarm = allDayAlarm("2026-07-15", row, zone = newYork)

        assertThat(firedAt(alarm, newYork).toLocalDateTime())
            .isEqualTo(LocalDate.parse("2026-07-14").atTime(9, 0))
    }

    @Test
    fun `a zero-day row fires on the event's own date`() {
        assertThat(allDayLeadDays(rawMinutes = 0, beginMillis = allDayBegin("2026-07-15"), zone = berlin))
            .isEqualTo(0L)
    }

    @Test
    fun `a timed reminder is exact across a DST boundary`() {
        // Nothing to re-anchor: begin is an absolute instant either way.
        val begin = LocalDate.parse("2026-03-29").atTime(14, 0)
            .atZone(berlin).toInstant().toEpochMilli()
        val planned = plan(
            instances = listOf(instance(1L, beginMillis = begin, isAllDay = false)),
            minutesByEvent = mapOf(1L to listOf(30)),
            zone = berlin,
            allDayTimeMinutes = nineAm,
        )

        assertThat(firedAt(planned.single().alarmMillis).toLocalDateTime())
            .isEqualTo(LocalDate.parse("2026-03-29").atTime(13, 30))
    }

    @Test
    fun `every occurrence of a series gets its own reminder`() {
        val planned = plan(
            instances = listOf(
                instance(1L, beginMillis = now + day),
                instance(1L, beginMillis = now + 2 * day),
            ),
            minutesByEvent = mapOf(1L to listOf(15)),
        )

        assertThat(planned.map { it.alarmMillis })
            .containsExactly(now + day - 15 * minute, now + 2 * day - 15 * minute)
        assertThat(planned.map { it.key }.toSet()).hasSize(2)
    }

    @Test
    fun `duplicate reminder rows collapse to one`() {
        val planned = plan(
            instances = listOf(instance(1L)),
            minutesByEvent = mapOf(1L to listOf(10, 10)),
        )

        assertThat(planned).hasSize(1)
    }

    @Test
    fun `an event with no reminders plans nothing`() {
        val planned = plan(
            instances = listOf(instance(1L)),
            minutesByEvent = emptyMap(),
        )

        assertThat(planned).isEmpty()
    }

    @Test
    fun `the same reminder keeps its key across scans`() {
        val plan = {
            plan(listOf(instance(1L)), mapOf(1L to listOf(10))).single().key
        }

        assertThat(plan()).isEqualTo(plan())
    }

    @Test
    fun `occurrences of one series get different keys`() {
        val planned = plan(
            instances = listOf(
                instance(1L, beginMillis = now + day),
                instance(1L, beginMillis = now + 2 * day),
            ),
            minutesByEvent = mapOf(1L to listOf(15)),
        )

        assertThat(planned[0].key).isNotEqualTo(planned[1].key)
    }

    @Test
    fun `two reminders on one occurrence get different keys`() {
        val planned = plan(
            instances = listOf(instance(1L)),
            minutesByEvent = mapOf(1L to listOf(10, 30)),
        )

        assertThat(planned[0].key).isNotEqualTo(planned[1].key)
    }

    @Test
    fun `a reminder whose moment has passed since the last scan is due`() {
        val planned = plan(
            instances = listOf(instance(1L, beginMillis = now + 5 * minute)),
            minutesByEvent = mapOf(1L to listOf(10)),
        )

        val schedule = scheduleReminders(
            planned, lastFiredMillis = now - 10 * minute, nowMillis = now,
            horizonMillis = now + day,
        )

        assertThat(schedule.due).hasSize(1)
    }

    @Test
    fun `a reminder already covered by the watermark does not fire twice`() {
        // The scan runs again (a provider change, a reboot) after the alarm that
        // already posted this one. Nothing records "fired" but the watermark.
        val planned = plan(
            instances = listOf(instance(1L, beginMillis = now + 5 * minute)),
            minutesByEvent = mapOf(1L to listOf(10)),
        )

        val schedule = scheduleReminders(
            planned, lastFiredMillis = now - 4 * minute, nowMillis = now,
            horizonMillis = now + day,
        )

        assertThat(schedule.due).isEmpty()
    }

    @Test
    fun `a reminder exactly on the watermark does not fire again`() {
        val begin = now + 5 * minute
        val planned = plan(
            instances = listOf(instance(1L, beginMillis = begin)),
            minutesByEvent = mapOf(1L to listOf(10)),
        )
        val alarm = planned.single().alarmMillis

        val schedule = scheduleReminders(
            planned, lastFiredMillis = alarm, nowMillis = now, horizonMillis = now + day,
        )

        assertThat(schedule.due).isEmpty()
    }

    @Test
    fun `a missed alarm is caught up by a much later scan`() {
        // The device was off over the reminder; the scan on boot must still post it
        // while the event is ahead. This is what the provider path could never do.
        val planned = plan(
            instances = listOf(instance(1L, beginMillis = now + 5 * minute)),
            minutesByEvent = mapOf(1L to listOf(60)),
        )

        val schedule = scheduleReminders(
            planned, lastFiredMillis = now - 5 * day, nowMillis = now,
            horizonMillis = now + day,
        )

        assertThat(schedule.due).hasSize(1)
    }

    @Test
    fun `a reminder for an occurrence that already ended is dropped`() {
        val planned = plan(
            instances = listOf(
                instance(1L, beginMillis = now - 3 * 60 * minute, endMillis = now - 2 * 60 * minute),
            ),
            minutesByEvent = mapOf(1L to listOf(10)),
        )

        val schedule = scheduleReminders(
            planned, lastFiredMillis = now - 5 * day, nowMillis = now,
            horizonMillis = now + day,
        )

        assertThat(schedule.due).isEmpty()
    }

    @Test
    fun `an occurrence with no end falls back to its begin for relevance`() {
        val planned = plan(
            instances = listOf(
                instance(1L, beginMillis = now - minute, endMillis = 0L),
            ),
            minutesByEvent = mapOf(1L to listOf(10)),
        )

        val schedule = scheduleReminders(
            planned, lastFiredMillis = now - day, nowMillis = now, horizonMillis = now + day,
        )

        assertThat(schedule.due).isEmpty()
    }

    @Test
    fun `due reminders come out in occurrence order`() {
        val planned = plan(
            instances = listOf(
                instance(1L, beginMillis = now + 20 * minute),
                instance(2L, beginMillis = now + 5 * minute),
            ),
            minutesByEvent = mapOf(1L to listOf(30), 2L to listOf(30)),
        )

        val schedule = scheduleReminders(
            planned, lastFiredMillis = now - day, nowMillis = now, horizonMillis = now + day,
        )

        assertThat(schedule.due.map { it.instance.eventId }).containsExactly(2L, 1L).inOrder()
    }

    @Test
    fun `the next wake-up is the earliest reminder still ahead`() {
        val planned = plan(
            instances = listOf(
                instance(1L, beginMillis = now + 20 * minute),
                instance(2L, beginMillis = now + 90 * minute),
            ),
            minutesByEvent = mapOf(1L to listOf(5), 2L to listOf(5)),
        )

        val schedule = scheduleReminders(
            planned, lastFiredMillis = now, nowMillis = now, horizonMillis = now + day,
        )

        assertThat(schedule.nextAlarmMillis).isEqualTo(now + 15 * minute)
    }

    @Test
    fun `with nothing pending the scan still re-runs at the horizon`() {
        val schedule = scheduleReminders(
            planned = emptyList(), lastFiredMillis = now, nowMillis = now,
            horizonMillis = now + day,
        )

        assertThat(schedule.nextAlarmMillis).isEqualTo(now + day)
    }

    @Test
    fun `a reminder beyond the horizon waits for the next scan`() {
        // Capping keeps the rolling window honest: the far-off reminder is picked
        // up by a later scan rather than pinned to an alarm we may never re-check.
        val planned = plan(
            instances = listOf(instance(1L, beginMillis = now + 30 * day)),
            minutesByEvent = mapOf(1L to listOf(5)),
        )

        val schedule = scheduleReminders(
            planned, lastFiredMillis = now, nowMillis = now, horizonMillis = now + day,
        )

        assertThat(schedule.nextAlarmMillis).isEqualTo(now + day)
    }

    @Test
    fun `the query horizon stretches past the longest reminder offset`() {
        // A "2 weeks before" reminder has to be planned while its event is still
        // outside the plain lookahead, or it fires late.
        assertThat(reminderQueryHorizon(lookaheadMillis = 7 * day, maxReminderMinutes = 20_160))
            .isEqualTo(7 * day + 14 * day)
    }

    @Test
    fun `a negative longest offset does not shrink the query horizon`() {
        assertThat(reminderQueryHorizon(lookaheadMillis = 7 * day, maxReminderMinutes = -420))
            .isEqualTo(7 * day)
    }
}
