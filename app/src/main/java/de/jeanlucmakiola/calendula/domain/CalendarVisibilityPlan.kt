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
 * Reconcile [pendingDisabledIds] — switch-offs the app could not write, plus
 * what the retired app-local visibility model left behind (#75) — against the
 * calendars actually on the device.
 *
 * Only ever *hides*: switching a system-hidden calendar back on would un-hide it
 * in every other calendar app too. Calendula follows the flag and explains
 * itself once (see [hasSystemHiddenCalendars]).
 *
 * [CalendarVisibilityPlan.settled] carries the ids needing no write — already
 * hidden, or gone from the device.
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
        // Gone from the device, or already invisible — nothing to write.
        if (calendar != null && calendar.isVisibleInSystem) hide += id else settled += id
    }
    return CalendarVisibilityPlan(hide = hide, settled = settled)
}

/**
 * Whether any calendar is switched off at system level without Calendula having
 * asked for it — the condition the one-time notice explains.
 */
fun hasSystemHiddenCalendars(
    calendars: List<CalendarSource>,
    pendingDisabledIds: Set<Long>,
): Boolean = calendars.any { !it.isVisibleInSystem && it.id !in pendingDisabledIds }

/**
 * The failure a calendar list implies, or null when there is something to show.
 * Distinguishes "the device has no calendars" from "every one of them is
 * switched off" (#239) — both leave the grid empty, but only the second is
 * fixed from Settings → Calendars.
 *
 * The filter sheet's own hidden set is deliberately not folded in: emptying it
 * is a deliberate act taken moments earlier, in a sheet that is still one tap
 * away.
 */
fun calendarListFailure(calendars: List<CalendarSource>): FailureReason? = when {
    calendars.isEmpty() -> FailureReason.NoCalendarsConfigured
    calendars.none { it.isVisibleInSystem } -> FailureReason.AllCalendarsHidden
    else -> null
}
