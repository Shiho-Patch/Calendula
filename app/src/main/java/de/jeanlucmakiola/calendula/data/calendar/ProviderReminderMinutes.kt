package de.jeanlucmakiola.calendula.data.calendar

import android.provider.CalendarContract

/**
 * "Fire at whatever lead time this account defaults to" — a sentinel in the
 * `Reminders.MINUTES` column, not an offset, so nothing may do arithmetic on it.
 * `CalendarContract` never exposes what an account resolves it to, so Calendula
 * neither fires such a row ([withoutProviderDefaults]) nor decodes it
 * ([fromProviderAllDayMinutes]); the detail screen names it instead.
 *
 * It is the **only** negative that means this. A real all-day offset is often
 * negative too — the firing time of day is encoded into it — so a `< 0` test
 * would silence reminders that do fire.
 */
internal const val MINUTES_DEFAULT = CalendarContract.Reminders.MINUTES_DEFAULT

/**
 * Raw provider offsets with the account-default rows dropped, so the pure
 * planner ([de.jeanlucmakiola.calendula.domain.reminders.planReminders]) only
 * ever sees offsets it can resolve.
 */
internal fun Map<Long, List<Int>>.withoutProviderDefaults(): Map<Long, List<Int>> =
    mapValues { (_, minutes) -> minutes.filter { it != MINUTES_DEFAULT } }
