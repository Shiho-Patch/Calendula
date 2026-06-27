package de.jeanlucmakiola.calendula.ui.agenda

/**
 * How far ahead the agenda (screen or widget) shows events, as a rolling window
 * starting at today. [Day]/[Week]/[Month] are fixed 1/7/30-day windows; [Custom]
 * is an arbitrary day count. Rolling (not calendar-aligned) windows so the span
 * never degenerates near a week/month boundary.
 */
sealed interface AgendaRange {
    data object Day : AgendaRange
    data object Week : AgendaRange
    data object Month : AgendaRange
    data class Custom(val days: Int) : AgendaRange

    companion object {
        /** Allowed bounds for a [Custom] day count. */
        const val MIN_CUSTOM_DAYS = 1
        const val MAX_CUSTOM_DAYS = 365
    }
}

/** Inclusive number of days the window spans, starting at (and including) today. */
fun AgendaRange.dayCount(): Int = when (this) {
    AgendaRange.Day -> 1
    AgendaRange.Week -> 7
    AgendaRange.Month -> 30
    is AgendaRange.Custom -> days.coerceIn(AgendaRange.MIN_CUSTOM_DAYS, AgendaRange.MAX_CUSTOM_DAYS)
}

/** Stored representation: a fixed token, or `CUSTOM:<days>`. */
fun AgendaRange.storageValue(): String = when (this) {
    AgendaRange.Day -> "DAY"
    AgendaRange.Week -> "WEEK"
    AgendaRange.Month -> "MONTH"
    is AgendaRange.Custom -> "$CUSTOM_PREFIX$days"
}

/** Parse a stored value; unknown/garbage falls back to [default]. */
fun parseAgendaRange(stored: String?, default: AgendaRange): AgendaRange = when {
    stored == "DAY" -> AgendaRange.Day
    stored == "WEEK" -> AgendaRange.Week
    stored == "MONTH" -> AgendaRange.Month
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
