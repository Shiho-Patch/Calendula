package de.jeanlucmakiola.calendula.ui.agenda

import de.jeanlucmakiola.floret.locale.localizedDateFormatter
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * How far ahead the agenda (screen or widget) shows events, starting at today.
 *
 * Two flavours:
 * - **Rolling** windows of a fixed length: [Day] (today), [Week] (7 days),
 *   [Month] (30 days), [Custom] (1–365 days). These never degenerate.
 * - **Calendar-aligned** windows that end at a period boundary: [ThisWeek] runs
 *   through the end of the current week (respecting the week-start preference —
 *   a Monday start means "everything before next Monday"); [ThisMonth] runs
 *   through the last day of the current month. These shrink as the period ends.
 */
sealed interface AgendaRange {
    data object Day : AgendaRange
    data object Week : AgendaRange
    data object Month : AgendaRange
    data object ThisWeek : AgendaRange
    data object ThisMonth : AgendaRange
    data class Custom(val days: Int) : AgendaRange

    companion object {
        /** Allowed bounds for a [Custom] day count. */
        const val MIN_CUSTOM_DAYS = 1
        const val MAX_CUSTOM_DAYS = 365
    }
}

private const val DAYS_PER_WEEK = 7

/**
 * Inclusive number of days the window spans, starting at (and including)
 * [anchor]. The calendar-aligned ranges depend on the anchor day and, for
 * [AgendaRange.ThisWeek], the [weekStart] preference.
 */
fun AgendaRange.dayCount(anchor: LocalDate, weekStart: DayOfWeek): Int = when (this) {
    AgendaRange.Day -> 1
    AgendaRange.Week -> DAYS_PER_WEEK
    AgendaRange.Month -> 30
    AgendaRange.ThisWeek -> {
        // Days elapsed since the week's first day (0 when today *is* the start),
        // so the window is the rest of the week through the day before it repeats.
        val sinceWeekStart = ((anchor.dayOfWeek.ordinal - weekStart.ordinal) + DAYS_PER_WEEK) % DAYS_PER_WEEK
        DAYS_PER_WEEK - sinceWeekStart
    }
    AgendaRange.ThisMonth -> {
        val daysInMonth = YearMonth.of(anchor.year, anchor.month.ordinal + 1).lengthOfMonth()
        daysInMonth - anchor.day + 1
    }
    is AgendaRange.Custom -> days.coerceIn(AgendaRange.MIN_CUSTOM_DAYS, AgendaRange.MAX_CUSTOM_DAYS)
}

/** Stored representation: a fixed token, or `CUSTOM:<days>`. */
fun AgendaRange.storageValue(): String = when (this) {
    AgendaRange.Day -> "DAY"
    AgendaRange.Week -> "WEEK"
    AgendaRange.Month -> "MONTH"
    AgendaRange.ThisWeek -> "THIS_WEEK"
    AgendaRange.ThisMonth -> "THIS_MONTH"
    is AgendaRange.Custom -> "$CUSTOM_PREFIX$days"
}

/** Parse a stored value; unknown/garbage falls back to [default]. */
fun parseAgendaRange(stored: String?, default: AgendaRange): AgendaRange = when {
    stored == "DAY" -> AgendaRange.Day
    stored == "WEEK" -> AgendaRange.Week
    stored == "MONTH" -> AgendaRange.Month
    stored == "THIS_WEEK" -> AgendaRange.ThisWeek
    stored == "THIS_MONTH" -> AgendaRange.ThisMonth
    stored != null && stored.startsWith(CUSTOM_PREFIX) -> {
        val days = stored.removePrefix(CUSTOM_PREFIX).toIntOrNull()
        if (days != null) {
            AgendaRange.Custom(days.coerceIn(AgendaRange.MIN_CUSTOM_DAYS, AgendaRange.MAX_CUSTOM_DAYS))
        } else {
            default
        }
    }
    else -> default
}

/**
 * The concrete span the range covers, starting at [start] through [end]
 * (inclusive), for a human-readable header. Both ends use the same day-month
 * order so a span never mixes "15 Jul" with "Aug 13, 2026":
 * - [AgendaRange.Day] → a single date ("27 Jun 2026")
 * - [AgendaRange.ThisMonth] → month and year ("June 2026")
 * - everything else → "start – end" ("27 Jun – 3 Jul 2026"), with the start's
 *   year shown too only when it differs from the end's.
 */
fun agendaRangeWindowSummary(
    range: AgendaRange,
    start: LocalDate,
    end: LocalDate,
    locale: Locale,
): String {
    val javaStart = java.time.LocalDate.of(start.year, start.month.ordinal + 1, start.day)
    val javaEnd = java.time.LocalDate.of(end.year, end.month.ordinal + 1, end.day)
    val dayMonth = localizedDateFormatter(locale, "dMMM")
    val dayMonthYear = localizedDateFormatter(locale, "dMMMy")
    return when (range) {
        AgendaRange.Day -> dayMonthYear.format(javaStart)
        AgendaRange.ThisMonth -> localizedDateFormatter(locale, "LLLLy").format(javaStart)
        else -> {
            val startFmt = if (start.year == end.year) dayMonth else dayMonthYear
            "${startFmt.format(javaStart)} – ${dayMonthYear.format(javaEnd)}"
        }
    }
}

private const val CUSTOM_PREFIX = "CUSTOM:"
