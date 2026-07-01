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
    if (available.isEmpty()) return this
    val i = available.indexOf(this)
    if (i < 0) return available.first()
    return available[(i + 1) % available.size]
}

/**
 * The user's customisation of the top-bar quick-switch button (#24): which views
 * it cycles through ([enabled]) and in what [order]. [order] always lists every
 * implemented view — the settings screen reorders the whole set — while [cycle]
 * is the subset the pill actually steps through, in [order]. The navigation
 * drawer keeps its own separate order and always lists every view, so a view
 * disabled here stays reachable there.
 */
data class QuickSwitchConfig(
    val order: List<CalendarView>,
    val enabled: Set<CalendarView>,
) {
    /** Views the pill steps through, in [order]. */
    val cycle: List<CalendarView> get() = order.filter { it in enabled }

    companion object {
        /** All views, in default order, all enabled. */
        val Default = QuickSwitchConfig(IMPLEMENTED_VIEWS, IMPLEMENTED_VIEWS.toSet())

        /**
         * Fewest views that keep the switch meaningful — a "switch" needs at
         * least two targets, so the settings screen blocks disabling below this.
         */
        const val MIN_ENABLED = 2
    }
}

/**
 * The top-level view back stack (bottom → top): the [default] home view always
 * sits at the bottom. Pressing back pops one level until only the home view
 * remains, then the system exits the app. Three operations move it:
 *
 * - [selectView] — a lateral switch (the pill or the drawer). It keeps a visit
 *   history so back retraces it: switching to a not-yet-visited view pushes it on
 *   top; switching to one already in the stack pops back to it (collapsing the
 *   loop, so the depth stays bounded by the number of distinct views). Selecting
 *   the home view collapses to just `[default]`.
 * - [viewBaseStack] — the stack a widget launch resets to: just `[default]` when
 *   the widget's view is the home view, else `[default, source]`. A widget entry
 *   starts a fresh context rather than extending in-app history.
 * - [drillToDay] — pushes the day view on top (a date tap from the month grid or
 *   a widget), unless the day view is already current.
 */
fun List<CalendarView>.selectView(target: CalendarView): List<CalendarView> {
    val existing = indexOf(target)
    return if (existing >= 0) take(existing + 1) else this + target
}

fun viewBaseStack(default: CalendarView, source: CalendarView): List<CalendarView> =
    if (source == default) listOf(default) else listOf(default, source)

/** Push the day view as a drill-in over the current stack (no-op if already on it). */
fun List<CalendarView>.drillToDay(): List<CalendarView> =
    if (lastOrNull() == CalendarView.Day) this else this + CalendarView.Day
