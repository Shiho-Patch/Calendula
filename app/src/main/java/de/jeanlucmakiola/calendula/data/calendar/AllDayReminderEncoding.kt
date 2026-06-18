package de.jeanlucmakiola.calendula.data.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Translates an all-day reminder between the **semantic** lead time the UI
 * speaks (whole days before the event — "1 day before") and the **raw**
 * `CalendarContract.Reminders.MINUTES` offset the provider stores.
 *
 * Calendula schedules no alarms itself: the provider fires a reminder at
 * `DTSTART − MINUTES` (the Etar model). An all-day event's DTSTART is **UTC
 * midnight** (see [EventWriteTimes]), so a raw `MINUTES = 1440` ("1 day") lands
 * on UTC-midnight of the previous day — 02:00 local in CEST, not the morning.
 *
 * To fire at a chosen wall-clock time we encode that time *into* the offset:
 * `MINUTES = UTC-midnight(startDate) − (localInstant of [timeOfDayMinutes] on the
 * day [semanticMinutes] before)`. The single fixed offset can only be tuned for
 * the event's own date, so a recurring all-day series or a post-creation
 * timezone change drifts the fire time by the offset delta (±1h across DST) —
 * an inherent limit of the provider model, shared by Etar.
 */
private const val MINUTES_PER_DAY = 1_440
private const val MILLIS_PER_MINUTE = 60_000L

/**
 * Raw provider `MINUTES` for an all-day reminder set [semanticMinutes] before the
 * event (a whole-day multiple; sub-day remainders are dropped), so it fires at
 * [timeOfDayMinutes] (minutes from local midnight) in [zone]. The result may be
 * **negative** — e.g. "at time of event" at 09:00 CEST encodes to −420, meaning
 * the provider fires *after* DTSTART; this is valid and must not be clamped.
 * A negative [semanticMinutes] is the "provider default" sentinel and passes
 * through unchanged.
 */
internal fun toProviderAllDayMinutes(
    semanticMinutes: Int,
    startDate: LocalDate,
    zone: ZoneId,
    timeOfDayMinutes: Int,
): Int {
    if (semanticMinutes < 0) return semanticMinutes
    val utcMidnight = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val fire = startDate.minusDays((semanticMinutes / MINUTES_PER_DAY).toLong())
        .atTime(LocalTime.of(timeOfDayMinutes / 60, timeOfDayMinutes % 60))
        .atZone(zone).toInstant().toEpochMilli()
    return ((utcMidnight - fire) / MILLIS_PER_MINUTE).toInt()
}

/**
 * Recover the semantic whole-day lead time from a raw all-day reminder
 * [rawMinutes]. Keys off the **local date** of the encoded fire instant, so it
 * returns the right day count regardless of which [timeOfDayMinutes] wrote the
 * row — including pre-feature rows (raw multiples of 1440, fired at UTC midnight)
 * and rows written under a different timezone. A negative [rawMinutes] (fire
 * after DTSTART) folds to day 0.
 */
internal fun fromProviderAllDayMinutes(
    rawMinutes: Int,
    startDate: LocalDate,
    zone: ZoneId,
): Int {
    val utcMidnight = startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val fireLocalDate = Instant.ofEpochMilli(utcMidnight - rawMinutes * MILLIS_PER_MINUTE)
        .atZone(zone).toLocalDate()
    return ChronoUnit.DAYS.between(fireLocalDate, startDate).toInt() * MINUTES_PER_DAY
}
