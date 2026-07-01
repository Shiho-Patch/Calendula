package de.jeanlucmakiola.calendula.data.calendar

import de.jeanlucmakiola.calendula.domain.contacts.SpecialDateType

/**
 * One of the app's special-dates calendars, discovered by the marker stamped in
 * its `CAL_SYNC2` column — so the mirror can re-adopt its calendars even if the
 * stored ids in preferences were lost (e.g. an app-data wipe).
 */
data class ManagedCalendarRow(val id: Long, val type: SpecialDateType)

/**
 * A managed event as read back for the sync diff. [uid] is the deterministic
 * `Events.UID_2445` (`contact-<type>:<lookupKey>@calendula`) the mirror keys on;
 * [title], [dtStartMillis] and [rrule] are the managed columns compared against
 * the desired state to decide whether a targeted update is needed.
 */
data class ManagedEventRow(
    val eventId: Long,
    val uid: String,
    val title: String,
    val dtStartMillis: Long,
    val rrule: String?,
)
