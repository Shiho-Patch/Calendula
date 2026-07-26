package de.jeanlucmakiola.calendula.domain.reminders

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

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
private const val MINUTES_PER_DAY = 1_440

/**
 * Pair every instance with each of its event's reminder offsets.
 *
 * A **timed** occurrence is trivial: `begin` is an absolute instant, so
 * `begin − minutes` is exact by construction, in any timezone, across any DST
 * boundary.
 *
 * An **all-day** occurrence is not, and taking the offset at face value is what
 * makes reminders land at the wrong hour. Its `begin` is UTC midnight, and the
 * stored offset is not a plain lead time — `AllDayReminderEncoding` folds the
 * wanted wall-clock hour into it, sampled against *one* date's UTC offset. Fire
 * at `begin − minutes` and every occurrence in a different DST phase than the one
 * that was sampled drifts by the offset delta, an hour early in one direction and
 * an hour late in the other. Rows written by other apps carry no wall-clock at
 * all — a conventional `1440` fires at UTC midnight, which is 01:00 or 02:00
 * local in Berlin and the wrong day west of UTC.
 *
 * So the offset is only read for *which day* it means, via [allDayLeadDays], and
 * the hour comes from [allDayTimeMinutes] — the one global "show all-day
 * reminders at" setting — recomposed against each occurrence's own date in
 * [zone]. 09:00 Berlin is then 09:00 Berlin on every occurrence, whatever the
 * offset was when the row was written.
 *
 * [minutesByEvent] may hold duplicate offsets (two identical reminder rows on one
 * event); they collapse, because they would otherwise fight over one notification.
 */
fun planReminders(
    instances: List<ReminderEventInstance>,
    minutesByEvent: Map<Long, List<Int>>,
    zone: ZoneId,
    allDayTimeMinutes: Int,
): List<PlannedReminder> = instances.flatMap { instance ->
    minutesByEvent[instance.eventId].orEmpty().distinct().map { minutes ->
        PlannedReminder(
            instance = instance,
            minutes = minutes,
            alarmMillis = if (instance.isAllDay) {
                allDayAlarmMillis(instance.beginMillis, minutes, zone, allDayTimeMinutes)
            } else {
                instance.beginMillis - minutes * MILLIS_PER_MINUTE
            },
        )
    }
}

/** UTC midnight of an all-day occurrence, as the calendar date it stands for. */
private fun allDayDate(beginMillis: Long): LocalDate =
    Instant.ofEpochMilli(beginMillis).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * How many whole days before its occurrence a raw all-day offset means.
 *
 * A plain multiple of 1440 is read at face value. That covers rows from other
 * calendar apps, which carry no encoded hour — and it stays right for our own
 * rows that happen to land on a multiple, because those encode a wall-clock hour
 * equal to the sampled UTC offset, so the day count is the same either way.
 *
 * Anything else is one of ours, with an hour folded in: recover the day count the
 * way [de.jeanlucmakiola.calendula.data.calendar.fromProviderAllDayMinutes]
 * does for display, by asking which local date the encoded instant falls on.
 * Keeping the two in step is what makes the notification arrive on the day the
 * event screen says it will.
 */
internal fun allDayLeadDays(rawMinutes: Int, beginMillis: Long, zone: ZoneId): Long {
    if (rawMinutes % MINUTES_PER_DAY == 0) return (rawMinutes / MINUTES_PER_DAY).toLong()
    val encoded = Instant.ofEpochMilli(beginMillis - rawMinutes * MILLIS_PER_MINUTE)
    return ChronoUnit.DAYS.between(encoded.atZone(zone).toLocalDate(), allDayDate(beginMillis))
}

private fun allDayAlarmMillis(
    beginMillis: Long,
    rawMinutes: Int,
    zone: ZoneId,
    allDayTimeMinutes: Int,
): Long = allDayDate(beginMillis)
    .minusDays(allDayLeadDays(rawMinutes, beginMillis, zone))
    .atTime(LocalTime.of(allDayTimeMinutes / 60, allDayTimeMinutes % 60))
    .atZone(zone)
    .toInstant()
    .toEpochMilli()

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
 * The watermark a scan at [nowMillis] should measure against, given what the
 * last one recorded.
 *
 * A first-ever scan ([lastScanMillis] `null`) claims the present, so an install
 * or an upgrade onto in-house delivery does not treat every reminder since the
 * epoch as overdue and bury the user in notifications. A watermark in the
 * *future* — the clock was moved back, or the user travelled across the date
 * line — is clamped for the mirror-image reason: left alone it would silence
 * every reminder until real time caught up with it.
 */
fun reminderWatermark(lastScanMillis: Long?, nowMillis: Long): Long =
    lastScanMillis?.coerceAtMost(nowMillis) ?: nowMillis

/**
 * How far ahead instances must be queried for [scheduleReminders] to see every
 * reminder in time: the plain lookahead plus the longest offset any reminder row
 * carries, so a "two weeks before" reminder is planned before it comes due
 * instead of firing late (the limitation Etar's equivalent documents).
 */
fun reminderQueryHorizon(lookaheadMillis: Long, maxReminderMinutes: Int): Long =
    lookaheadMillis + maxOf(0L, maxReminderMinutes * MILLIS_PER_MINUTE)
