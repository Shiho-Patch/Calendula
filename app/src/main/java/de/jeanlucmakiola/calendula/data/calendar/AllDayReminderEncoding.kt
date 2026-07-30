package de.jeanlucmakiola.calendula.data.calendar

import de.jeanlucmakiola.calendula.domain.reminders.allDayLeadDays
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

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
 * The next date on or after [today] carrying [month]/[day] — the date to sample
 * a yearly all-day reminder's UTC offset at. A managed birthday's DTSTART is an
 * ancient anchor (1972 for year-less dates), a year whose timezone rules
 * (pre-DST offsets) differ from today's; sampling the offset there skews every
 * modern occurrence by hours. Sampling at the upcoming occurrence makes the
 * stored offset correct for it and its neighbours (only ±1h DST drift remains,
 * the inherent limit). Feb-29 skips forward to the next leap year.
 */
internal fun nextYearlyOccurrence(month: Int, day: Int, today: LocalDate): LocalDate {
    var year = today.year
    // A Feb-29 date is valid only every ~4 years; 8 tries always reaches one.
    repeat(8) {
        val candidate = runCatching { LocalDate.of(year, month, day) }.getOrNull()
        if (candidate != null && !candidate.isBefore(today)) return candidate
        year++
    }
    // Unreachable for real month/day inputs; fall back to the raw anchor.
    return LocalDate.of(today.year, month, day)
}

/**
 * Recover the semantic whole-day lead time from a raw all-day reminder
 * [rawMinutes] — the inverse of [toProviderAllDayMinutes], for the form and the
 * detail screen. Delegates to [allDayLeadDays] so what is displayed is the day
 * the reminder actually fires on; see there for how an encoded row is told from
 * a plain one written by another calendar app.
 */
internal fun fromProviderAllDayMinutes(
    rawMinutes: Int,
    startDate: LocalDate,
    zone: ZoneId,
    timeOfDayMinutes: Int,
): Int = allDayLeadDays(rawMinutes, startDate, zone, timeOfDayMinutes).toInt() * MINUTES_PER_DAY
