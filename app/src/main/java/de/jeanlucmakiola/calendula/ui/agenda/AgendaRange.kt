package de.jeanlucmakiola.calendula.ui.agenda

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import java.time.YearMonth

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

private const val CUSTOM_PREFIX = "CUSTOM:"
