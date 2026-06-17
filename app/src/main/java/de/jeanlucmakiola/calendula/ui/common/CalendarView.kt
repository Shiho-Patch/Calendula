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
