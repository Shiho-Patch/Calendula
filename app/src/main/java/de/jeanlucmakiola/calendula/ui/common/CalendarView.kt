package de.jeanlucmakiola.calendula.ui.common

/**
 * The top-level calendar views the user can switch between (spec M1).
 * Day is declared but not yet implemented (v0.5) — see [IMPLEMENTED_VIEWS].
 */
enum class CalendarView {
    Month,
    Week,
    Day,
}

/**
 * Views that actually have a screen today. The view-switcher pill cycles
 * through these in order; Day joins once its screen lands.
 */
val IMPLEMENTED_VIEWS: List<CalendarView> = listOf(CalendarView.Month, CalendarView.Week)

/** Next view in [available], wrapping around. Falls back to Month if absent. */
fun CalendarView.next(available: List<CalendarView> = IMPLEMENTED_VIEWS): CalendarView {
    val i = available.indexOf(this)
    if (i < 0) return available.firstOrNull() ?: CalendarView.Month
    return available[(i + 1) % available.size]
}
