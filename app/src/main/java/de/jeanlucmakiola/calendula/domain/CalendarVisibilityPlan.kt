package de.jeanlucmakiola.calendula.domain

/**
 * The `Calendars.VISIBLE` writes that flush the app's pending "switched off"
 * set into the provider, plus the ids that need no write at all.
 */
data class CalendarVisibilityPlan(
    val hide: Set<Long> = emptySet(),
    val settled: Set<Long> = emptySet(),
) {
    val isEmpty: Boolean get() = hide.isEmpty() && settled.isEmpty()
}

/**
 * Reconcile [pendingDisabledIds] — calendars switched off in Settings →
 * Calendars while the app could not write `Calendars.VISIBLE`, plus whatever
 * the retired app-local visibility model left behind (#75) — against the
 * calendars actually on the device.
 *
 * The plan only ever *hides*. Switching a calendar off is intent the user
 * expressed in Calendula, so carrying it into the provider is fair. The other
 * direction is deliberately absent: a calendar hidden at system level was hidden
 * somewhere else (another calendar app, the account's own settings), and
 * switching it back on would un-hide it there too *and* start firing reminders
 * nobody asked for. Calendula follows that flag instead and explains itself once
 * (see [hasSystemHiddenCalendars]).
 *
 * [CalendarVisibilityPlan.settled] carries the ids that need no write — already
 * hidden, or gone from the device. They leave the pending set exactly as a
 * successful write would.
 */
fun calendarVisibilityPlan(
    calendars: List<CalendarSource>,
    pendingDisabledIds: Set<Long>,
): CalendarVisibilityPlan {
    val byId = calendars.associateBy { it.id }
    val hide = mutableSetOf<Long>()
    val settled = mutableSetOf<Long>()
    for (id in pendingDisabledIds) {
        val calendar = byId[id]
        // No row means the calendar is gone; already invisible means someone
        // (us, on an earlier run) got there first. Either way: nothing to write.
        if (calendar != null && calendar.isVisibleInSystem) hide += id else settled += id
    }
    return CalendarVisibilityPlan(hide = hide, settled = settled)
}

/**
 * Whether any calendar is switched off at system level without Calendula having
 * asked for it. Those calendars showed their events before the app adopted
 * `Calendars.VISIBLE` as its one visibility model and no longer do, which is
 * what the one-time notice explains — the alternative, switching them on, would
 * reach into every other calendar app on the device.
 */
fun hasSystemHiddenCalendars(
    calendars: List<CalendarSource>,
    pendingDisabledIds: Set<Long>,
): Boolean = calendars.any { !it.isVisibleInSystem && it.id !in pendingDisabledIds }
