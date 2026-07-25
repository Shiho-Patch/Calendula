package de.jeanlucmakiola.calendula.domain

/**
 * The `Calendars.VISIBLE` writes that bring the system flag in line with the
 * app's retired disabled set. Empty when the two already agree.
 */
data class CalendarVisibilityPlan(
    val show: Set<Long> = emptySet(),
    val hide: Set<Long> = emptySet(),
) {
    val isEmpty: Boolean get() = show.isEmpty() && hide.isEmpty()
}

/**
 * Reconcile the two visibility models Calendula used to keep in parallel (#75),
 * with the app's state winning:
 *
 * - enabled in-app but hidden at system level → show it, *unless* the calendar
 *   doesn't sync its events to the device ([CalendarSource.syncsEvents]). Those
 *   hold no local events, so switching them on would produce nothing but an
 *   "enabled yet permanently empty" row; left off, they read honestly as off.
 * - disabled in-app → hide it at system level, which is what stops the provider
 *   scheduling its reminders.
 * - anything already in agreement is left untouched, so the migration writes as
 *   little as possible.
 *
 * App-state-wins is also what keeps the upgrade invisible: reconciling the other
 * way would make events the user sees today vanish.
 */
fun calendarVisibilityPlan(
    calendars: List<CalendarSource>,
    disabledCalendarIds: Set<Long>,
): CalendarVisibilityPlan {
    val show = mutableSetOf<Long>()
    val hide = mutableSetOf<Long>()
    for (calendar in calendars) {
        val disabledInApp = calendar.id in disabledCalendarIds
        when {
            disabledInApp && calendar.isVisibleInSystem -> hide += calendar.id
            !disabledInApp && !calendar.isVisibleInSystem && calendar.syncsEvents ->
                show += calendar.id
        }
    }
    return CalendarVisibilityPlan(show = show, hide = hide)
}
