package de.jeanlucmakiola.calendula.ui.common

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.ui.graphics.vector.ImageVector
import de.jeanlucmakiola.calendula.R

/** The top-level calendar views the user can switch between (spec M1). */
enum class CalendarView {
    Month,
    Week,
    Day,
    Agenda,
}

/** Switcher label, shared by the top-bar pill and the drawer's View section. */
@get:StringRes
val CalendarView.labelRes: Int
    get() = when (this) {
        CalendarView.Month -> R.string.view_month
        CalendarView.Week -> R.string.view_week
        CalendarView.Day -> R.string.view_day
        CalendarView.Agenda -> R.string.view_agenda
    }

/** Leading icon for the view in the drawer's View section. */
val CalendarView.icon: ImageVector
    get() = when (this) {
        CalendarView.Month -> Icons.Filled.CalendarViewMonth
        CalendarView.Week -> Icons.Filled.CalendarViewWeek
        CalendarView.Day -> Icons.Filled.CalendarViewDay
        CalendarView.Agenda -> Icons.Filled.ViewAgenda
    }

/**
 * Views that actually have a screen today. The view-switcher pill cycles
 * through these in order.
 */
val IMPLEMENTED_VIEWS: List<CalendarView> =
    listOf(CalendarView.Month, CalendarView.Week, CalendarView.Day, CalendarView.Agenda)

/** Next view in [available], wrapping around. Falls back to Month if absent. */
fun CalendarView.next(available: List<CalendarView> = IMPLEMENTED_VIEWS): CalendarView {
    val i = available.indexOf(this)
    if (i < 0) return available.firstOrNull() ?: CalendarView.Month
    return available[(i + 1) % available.size]
}

/**
 * The top-level view back stack (bottom → top): the [default] home view always
 * sits at the bottom, with a laterally-selected or widget-entered view layered
 * above it. Pressing back pops one level until only the home view remains, then
 * the system exits the app.
 *
 * [viewBaseStack] is the stack after a lateral move to [top] (the switcher pill,
 * the drawer, or a widget establishing its own view): just `[default]` when
 * [top] is the home view, else `[default, top]`. It collapses any prior drill-in
 * so the depth never grows past a single lateral level. [drillToDay] pushes the
 * day view on top — a date tap from the month grid or a widget — unless the day
 * view is already current.
 */
fun viewBaseStack(default: CalendarView, top: CalendarView): List<CalendarView> =
    if (top == default) listOf(default) else listOf(default, top)

/** Push the day view as a drill-in over the current stack (no-op if already on it). */
fun List<CalendarView>.drillToDay(): List<CalendarView> =
    if (lastOrNull() == CalendarView.Day) this else this + CalendarView.Day
