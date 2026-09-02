package de.jeanlucmakiola.calendula.domain.reminders

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Pure decision layer of in-house reminder delivery (#75): instances and reminder
 * offsets in, fire instants out. No provider, no clock. See docs/ARCHITECTURE.md.
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
     * Stable identity, keying the notification tag and the snooze/dismiss
     * `PendingIntent`s. Survives reboot, re-scan and reinstall.
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
 * Timed occurrences fire at `begin − minutes`. All-day ones read the offset only
 * for *which day* it means ([allDayLeadDays]) and take the hour from
 * [allDayTimeMinutes], recomposed against each occurrence's own date in [zone].
 * Duplicate offsets in [minutesByEvent] collapse.
 *
 * Every offset given is planned. The provider's "use the account default"
 * sentinel names a lead time no reader can resolve, and is translated out on the
 * way in
 * ([de.jeanlucmakiola.calendula.data.calendar.withoutProviderDefaults]) — taken
 * as an offset it would arm an alarm a minute after the event began.
 */
fun planReminders(
    instances: List<ReminderEventInstance>,
    minutesByEvent: Map<Long, List<Int>>,
    zone: ZoneId,
    allDayTimeMinutes: Int,
): List<PlannedReminder> = instances.flatMap { instance ->
    minutesByEvent[instance.eventId].orEmpty()
        .distinct()
        .map { minutes ->
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
 * Our own rows fold the wanted hour into the offset, so the local date of the
 * encoded instant is the answer. A plain multiple of 1440 is a foreign bare lead
 * time and taken at face value instead — unless the instant lands on the hour the
 * setting names (within [NAMED_HOUR_TOLERANCE_MINUTES], for DST drift), where the
 * encodings collide and the tie goes to our own reading.
 *
 * Also used by
 * [de.jeanlucmakiola.calendula.data.calendar.fromProviderAllDayMinutes] for
 * display, so screen and notification agree.
 */
internal fun allDayLeadDays(
    rawMinutes: Int,
    startDate: LocalDate,
    zone: ZoneId,
    allDayTimeMinutes: Int,
): Long {
    val utcMidnight = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val encoded = Instant.ofEpochMilli(utcMidnight - rawMinutes * MILLIS_PER_MINUTE).atZone(zone)
    val minutesFromNamedHour = encoded.toLocalTime().let {
        val delta = (it.hour * 60 + it.minute - allDayTimeMinutes).mod(MINUTES_PER_DAY)
        minOf(delta, MINUTES_PER_DAY - delta)
    }
    if (rawMinutes % MINUTES_PER_DAY == 0 && minutesFromNamedHour > NAMED_HOUR_TOLERANCE_MINUTES) {
        return (rawMinutes / MINUTES_PER_DAY).toLong()
    }
    return ChronoUnit.DAYS.between(encoded.toLocalDate(), startDate)
}

/** DST drift a row written in the other phase carries, rounded up past Lord Howe's half hour. */
private const val NAMED_HOUR_TOLERANCE_MINUTES = 90

private fun allDayAlarmMillis(
    beginMillis: Long,
    rawMinutes: Int,
    zone: ZoneId,
    allDayTimeMinutes: Int,
): Long = allDayDate(beginMillis)
    .minusDays(allDayLeadDays(rawMinutes, allDayDate(beginMillis), zone, allDayTimeMinutes))
    .atTime(LocalTime.of(allDayTimeMinutes / 60, allDayTimeMinutes % 60))
    .atZone(zone)
    .toInstant()
    .toEpochMilli()

/**
 * Split [planned] into what is due now and when to wake up next.
 *
 * Due means the fire instant falls in `(lastFiredMillis, nowMillis]`, so a scan
 * running twice cannot post twice while a late one still catches up. Reminders
 * whose event has ended are dropped ([isStillRelevant]). [nextAlarmMillis] is
 * capped at [horizonMillis] so the lookahead window keeps rolling forward.
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
 * The watermark a scan at [nowMillis] should measure against. A first-ever scan
 * claims the present rather than replaying everything since the epoch; a
 * watermark in the future (clock moved back) is clamped so it can't silence
 * every reminder until real time catches up.
 */
fun reminderWatermark(lastScanMillis: Long?, nowMillis: Long): Long =
    lastScanMillis?.coerceAtMost(nowMillis) ?: nowMillis

/**
 * How far ahead instances must be queried: the plain lookahead plus the longest
 * reminder offset, so a "two weeks before" is planned before it comes due. The
 * stretch is capped at [MAX_REMINDER_LEAD_MILLIS] — `maxReminderMinutes` is the
 * largest row in the whole provider, and a nonsense one would otherwise make
 * every scan expand every series over years.
 */
fun reminderQueryHorizon(lookaheadMillis: Long, maxReminderMinutes: Int): Long =
    lookaheadMillis + (maxReminderMinutes * MILLIS_PER_MINUTE).coerceIn(0L, MAX_REMINDER_LEAD_MILLIS)

/** Longest reminder offset a scan stretches its query window for — one year. */
const val MAX_REMINDER_LEAD_MILLIS = 365L * 24 * 60 * 60 * 1000
