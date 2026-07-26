package de.jeanlucmakiola.calendula.domain.reminders

/**
 * Works out *when* each reminder has to fire and *which* ones are due, with no
 * provider and no clock of its own — the whole decision layer of in-house
 * reminder delivery (#75).
 *
 * Calendula used to leave both halves to the calendar provider: it scheduled the
 * alarms, wrote the `CalendarAlerts` rows, and broadcast `EVENT_REMINDER` at the
 * right moment. That chain is intact on stock Android but demonstrably not on
 * every device — AOSP's own unbundled calendar carries three separate
 * workarounds for OEMs that retarget the broadcast, or that only write the alert
 * row at alert time. An app that can only *react* to that broadcast has no way
 * to notice it never came.
 *
 * So the offsets in `CalendarContract.Reminders` are now read as data and turned
 * into alarms we own. Everything here is pure: instances and reminder offsets in,
 * fire instants out.
 */

/** An occurrence that reminders can hang off, flattened out of `Instances`. */
data class ReminderEventInstance(
    val eventId: Long,
    val calendarId: Long,
    val beginMillis: Long,
    val endMillis: Long,
    val title: String,
    val location: String?,
    val isAllDay: Boolean,
)

/**
 * One occurrence paired with one of its reminder offsets, and the instant that
 * pairing has to fire at.
 */
data class PlannedReminder(
    val instance: ReminderEventInstance,
    val minutes: Int,
    val alarmMillis: Long,
) {
    /**
     * Stable identity of this reminder, derived from what defines it rather
     * than from a provider row id (there is none any more). It keys the
     * notification tag and the snooze/dismiss `PendingIntent`s, so it has to
     * survive a reboot, a re-scan and a reinstall — the same reminder must land
     * on the same notification instead of stacking a second one.
     */
    val key: Long = key(instance.eventId, instance.beginMillis, minutes)

    private companion object {
        fun key(eventId: Long, beginMillis: Long, minutes: Int): Long {
            var h = eventId * 1_000_003L
            h = (h xor beginMillis) * 31L
            return h + minutes
        }
    }
}

/** What one scan concluded: post these now, and wake up again at [nextAlarmMillis]. */
data class ReminderSchedule(
    val due: List<PlannedReminder>,
    val nextAlarmMillis: Long,
)

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * Pair every instance with each of its event's reminder offsets.
 *
 * The fire instant is `begin − minutes` for timed **and** all-day occurrences
 * alike. That is not an oversight: an all-day instance's `begin` is UTC midnight,
 * and `AllDayReminderEncoding` deliberately encodes the wanted wall-clock time
 * *into* the stored offset measured from exactly that point. Applying a second,
 * timezone-dependent correction here would undo it.
 *
 * [minutesByEvent] may hold duplicate offsets (two identical reminder rows on one
 * event); they collapse, because they would otherwise fight over one notification.
 */
fun planReminders(
    instances: List<ReminderEventInstance>,
    minutesByEvent: Map<Long, List<Int>>,
): List<PlannedReminder> = instances.flatMap { instance ->
    minutesByEvent[instance.eventId].orEmpty().distinct().map { minutes ->
        PlannedReminder(
            instance = instance,
            minutes = minutes,
            alarmMillis = instance.beginMillis - minutes * MILLIS_PER_MINUTE,
        )
    }
}

/**
 * Split [planned] into what is due now and when to wake up next.
 *
 * Due means the fire instant falls in `(lastFiredMillis, nowMillis]` — a
 * half-open watermark, so a scan triggered twice cannot post the same reminder
 * twice, while a scan that runs late still catches everything the missed alarm
 * would have posted. That catch-up is the point: an alarm dropped by a reboot,
 * an app update or a doze window is recovered by the next scan rather than lost.
 *
 * A reminder whose event has already ended is dropped rather than posted late —
 * see [isStillRelevant].
 *
 * [nextAlarmMillis] is capped at [horizonMillis] even when nothing is pending, so
 * the scan re-runs at least that often and the lookahead window rolls forward.
 */
fun scheduleReminders(
    planned: List<PlannedReminder>,
    lastFiredMillis: Long,
    nowMillis: Long,
    horizonMillis: Long,
): ReminderSchedule {
    val due = planned
        .filter { it.alarmMillis in (lastFiredMillis + 1)..nowMillis }
        .filter { it.instance.isStillRelevant(nowMillis) }
        .distinctBy { it.key }
        .sortedWith(compareBy({ it.instance.beginMillis }, { it.key }))
    val nextPending = planned
        .filter { it.alarmMillis > nowMillis }
        .minOfOrNull { it.alarmMillis }
    return ReminderSchedule(
        due = due,
        nextAlarmMillis = minOf(nextPending ?: horizonMillis, horizonMillis),
    )
}

/**
 * Still worth showing while the occurrence has not ended. Falls back to the
 * begin time when the end is unknown (0L).
 */
fun ReminderEventInstance.isStillRelevant(nowMillis: Long): Boolean =
    (endMillis.takeIf { it > 0L } ?: beginMillis) >= nowMillis

/**
 * How far ahead instances must be queried for [scheduleReminders] to see every
 * reminder in time: the plain lookahead plus the longest offset any reminder row
 * carries, so a "two weeks before" reminder is planned before it comes due
 * instead of firing late (the limitation Etar's equivalent documents).
 */
fun reminderQueryHorizon(lookaheadMillis: Long, maxReminderMinutes: Int): Long =
    lookaheadMillis + maxOf(0L, maxReminderMinutes * MILLIS_PER_MINUTE)
