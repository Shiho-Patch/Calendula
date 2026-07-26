package de.jeanlucmakiola.calendula.domain.reminders

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

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

    @Test
    fun `a reminder fires its offset before the occurrence begins`() {
        val begin = now + 30 * minute
        val planned = planReminders(
            instances = listOf(instance(1L, beginMillis = begin)),
            minutesByEvent = mapOf(1L to listOf(10)),
        )

        assertThat(planned.map { it.alarmMillis }).containsExactly(begin - 10 * minute)
    }

    @Test
    fun `an all-day offset is measured from the raw begin, not corrected again`() {
        // An all-day instance begins at UTC midnight and AllDayReminderEncoding
        // already folded the wanted wall-clock time into the stored offset, so a
        // second timezone correction here would move the reminder off it.
        val utcMidnight = 1_700_006_400_000L
        val planned = planReminders(
            instances = listOf(instance(1L, beginMillis = utcMidnight, isAllDay = true)),
            minutesByEvent = mapOf(1L to listOf(1_020)),
        )

        assertThat(planned.single().alarmMillis).isEqualTo(utcMidnight - 1_020 * minute)
    }

    @Test
    fun `a negative offset fires after the occurrence begins`() {
        // "At time of event" on an all-day event encodes to a negative offset.
        val begin = now + 30 * minute
        val planned = planReminders(
            instances = listOf(instance(1L, beginMillis = begin, isAllDay = true)),
            minutesByEvent = mapOf(1L to listOf(-420)),
        )

        assertThat(planned.single().alarmMillis).isEqualTo(begin + 420 * minute)
    }

    @Test
    fun `every occurrence of a series gets its own reminder`() {
        val planned = planReminders(
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
        val planned = planReminders(
            instances = listOf(instance(1L)),
            minutesByEvent = mapOf(1L to listOf(10, 10)),
        )

        assertThat(planned).hasSize(1)
    }

    @Test
    fun `an event with no reminders plans nothing`() {
        val planned = planReminders(
            instances = listOf(instance(1L)),
            minutesByEvent = emptyMap(),
        )

        assertThat(planned).isEmpty()
    }

    @Test
    fun `the same reminder keeps its key across scans`() {
        val plan = {
            planReminders(listOf(instance(1L)), mapOf(1L to listOf(10))).single().key
        }

        assertThat(plan()).isEqualTo(plan())
    }

    @Test
    fun `occurrences of one series get different keys`() {
        val planned = planReminders(
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
        val planned = planReminders(
            instances = listOf(instance(1L)),
            minutesByEvent = mapOf(1L to listOf(10, 30)),
        )

        assertThat(planned[0].key).isNotEqualTo(planned[1].key)
    }

    @Test
    fun `a reminder whose moment has passed since the last scan is due`() {
        val planned = planReminders(
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
        val planned = planReminders(
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
        val planned = planReminders(
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
        val planned = planReminders(
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
        val planned = planReminders(
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
        val planned = planReminders(
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
        val planned = planReminders(
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
        val planned = planReminders(
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
        val planned = planReminders(
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
