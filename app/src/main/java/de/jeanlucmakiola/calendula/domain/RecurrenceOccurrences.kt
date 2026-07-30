package de.jeanlucmakiola.calendula.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/**
 * The first [limit] dates a [SimpleRecurrence] fires on, starting at [start]
 * (the event's own start date, i.e. DTSTART), for showing a rule as dates
 * instead of as words.
 *
 * A recurrence rule is the one place in the editor where a correct-sounding
 * phrase can still mean something the user didn't intend — "every 2 weeks on
 * Mon, Fri", "monthly" on the 31st — and the only honest answer is the dates
 * themselves. This is a *preview*, deliberately kept to the shapes the picker
 * can build; the provider remains the authority on what actually gets stored
 * and expanded.
 *
 * The rules it mirrors, all from RFC 5545 (the spec the platform's recurrence
 * engine implements):
 * - a monthly or yearly rule **skips** a period the start day doesn't exist in,
 *   rather than sliding to the month's last day: the 31st recurring monthly
 *   lands only in 31-day months, 29 February yearly only in leap years.
 * - a weekly rule with weekday picks repeats in blocks of `interval` weeks,
 *   with the week beginning on **Monday** — the RFC's default WKST, and the one
 *   that applies here because [toRRule] never writes a WKST part. The
 *   display-only "first day of week" preference deliberately doesn't enter into
 *   it, or the preview would disagree with the stored rule.
 * - [RecurrenceEnd.Count] counts real occurrences (skipped periods don't
 *   consume one), [RecurrenceEnd.Until] is inclusive of its date.
 *
 * Returns fewer than [limit] dates when the series ends first, and an empty
 * list when the rule yields nothing at all (an UNTIL before the start).
 */
fun SimpleRecurrence.upcomingOccurrences(start: LocalDate, limit: Int): List<LocalDate> {
    if (limit <= 0) return emptyList()
    val until = (end as? RecurrenceEnd.Until)?.date
    val maxCount = (end as? RecurrenceEnd.Count)?.times ?: Int.MAX_VALUE
    val wanted = minOf(limit, maxCount)
    if (wanted <= 0) return emptyList()

    val result = mutableListOf<LocalDate>()
    var period = 0
    // Periods that produce nothing (a skipped 31st, a weekday block whose picks
    // all fall before the start) must not stall the walk, so the cap is on
    // periods examined rather than on dates found.
    while (result.size < wanted && period < MAX_PERIODS) {
        for (date in occurrencesInPeriod(period, start)) {
            if (date < start) continue
            if (until != null && date > until) return result
            result += date
            if (result.size == wanted) return result
        }
        period++
    }
    return result
}

/** The dates this rule's [period]-th repetition yields (empty when skipped). */
private fun SimpleRecurrence.occurrencesInPeriod(period: Int, start: LocalDate): List<LocalDate> =
    when (freq) {
        RecurrenceFreq.Daily -> listOf(start.plus(period * interval, DateTimeUnit.DAY))
        RecurrenceFreq.Weekly -> weeklyOccurrences(period, start)
        RecurrenceFreq.Monthly -> {
            val month = start.plus(period * interval, DateTimeUnit.MONTH)
            // plus() clamps the day into the shorter month; the rule instead
            // skips such a period, so a clamped date means "not this month".
            listOfNotNull(dateOrNull(month.year, month.month.number, start.day))
        }
        RecurrenceFreq.Yearly ->
            listOfNotNull(dateOrNull(start.year + period * interval, start.month.number, start.day))
    }

/**
 * One weekly block: every picked weekday inside the week that begins
 * `period * interval` weeks after the start's own week, in weekday order. With
 * no picks the rule simply repeats the start's weekday.
 */
private fun SimpleRecurrence.weeklyOccurrences(period: Int, start: LocalDate): List<LocalDate> {
    if (byDays.isEmpty()) return listOf(start.plus(period * interval, DateTimeUnit.WEEK))
    val daysIntoWeek = (start.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber + DAYS_PER_WEEK) %
        DAYS_PER_WEEK
    val weekStart = start
        .minus(daysIntoWeek, DateTimeUnit.DAY)
        .plus(period * interval, DateTimeUnit.WEEK)
    return byDays.sortedBy { it.isoDayNumber }.map { day ->
        weekStart.plus(
            (day.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber + DAYS_PER_WEEK) % DAYS_PER_WEEK,
            DateTimeUnit.DAY,
        )
    }
}

/**
 * Whether a run of [occurrences] starting at [start] leaves its starting year —
 * i.e. whether showing them without a year would be ambiguous.
 *
 * Day and month alone are enough for a rule that stays inside one year, and
 * dropping the year keeps the line short. They are actively misleading
 * otherwise: a yearly rule reads as the same date three times over, and a
 * monthly one rolling past December gives no hint that it has.
 */
fun occurrencesSpanYears(occurrences: List<LocalDate>, start: LocalDate): Boolean =
    occurrences.any { it.year != start.year } || occurrences.map { it.year }.distinct().size > 1

/** [LocalDate] for a day-of-month that may not exist in that month; null if it doesn't. */
private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
    runCatching { LocalDate(year, month, day) }.getOrNull()

private const val DAYS_PER_WEEK = 7

/**
 * How many repetitions to examine before giving up. Generous enough for the
 * sparsest rule the picker can build (29 February yearly with a 999-year
 * interval is nonsense; 31st monthly needs at most a handful), and bounded so a
 * rule whose occurrences all fall outside its own UNTIL can't spin.
 */
private const val MAX_PERIODS = 2_000
