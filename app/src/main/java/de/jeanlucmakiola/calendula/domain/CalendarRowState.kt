package de.jeanlucmakiola.calendula.domain

/**
 * The ways a calendar can behave unlike a plain, writable one — each of them a
 * reason it is missing from the event and import pickers, and each of them
 * something the app knows and used to keep to itself (#76).
 */
enum class CalendarStateLabel {
    /**
     * A special-dates mirror the app fills from contacts. Writable and visible,
     * yet no event target: anything authored here is deleted by the next sync,
     * which is why it is the one exclusion with nothing else to give it away.
     */
    MANAGED,

    /** Contents can't be modified: a WebCal subscription, a read-only share. */
    READ_ONLY,

    /** The account holds the events, but this device isn't syncing them down. */
    NOT_SYNCED,
}

/**
 * Whether the account this calendar belongs to keeps its events off the device
 * (`Calendars.SYNC_EVENTS = 0`) — an "empty by construction" calendar: the rows
 * simply aren't here, so nothing can display them and no reminder can fire.
 *
 * Device-local calendars are excluded deliberately. Nothing syncs them by
 * definition, so the flag says nothing about them, and a local calendar from
 * another app can hold real events at `sync_events = 0` — the same unsoundness
 * that made the #75 migration guard wrong.
 */
val CalendarSource.isNotSynced: Boolean
    get() = !syncsEvents && !isLocal

/**
 * Whether a visibility switch on this calendar can change anything the user
 * would see. It can't for a non-syncing one: there are no events on the device
 * to reveal, so the switch would be a control that does nothing.
 */
val CalendarSource.hasVisibilitySwitch: Boolean
    get() = !isNotSynced

/**
 * Whether this calendar can be offered as a target for a new or imported event.
 * The one predicate behind both pickers, so the states [CalendarStateLabel]
 * names on a manager row are exactly the states that keep a calendar out of
 * them (#76):
 *
 * - read-only has nowhere to write;
 * - switched off would hide the event the moment it was saved;
 * - a managed mirror has the next contact sync delete it;
 * - a non-syncing one never carries the event up to the account, and
 *   `CalendarProvider2` wipes the calendar's rows outright when the
 *   subscription is switched back on — a saved event is a dead end either way.
 *
 * This is the test for *targets*. An event already living in an excluded
 * calendar keeps it; the editor adds that calendar back to its picker.
 */
val CalendarSource.isEventTarget: Boolean
    get() = canModifyContents && isVisibleInSystem && !isManaged && !isNotSynced

/** Every state worth naming on this calendar's row, in reading order. */
fun CalendarSource.stateLabels(): List<CalendarStateLabel> = buildList {
    if (isManaged) add(CalendarStateLabel.MANAGED)
    if (!canModifyContents) add(CalendarStateLabel.READ_ONLY)
    if (isNotSynced) add(CalendarStateLabel.NOT_SYNCED)
}

/**
 * Calendar-manager order within one group: the ones you can actually act on
 * first, the non-syncing ones after them. Stable otherwise, so the provider's
 * display-name ordering survives.
 */
fun List<CalendarSource>.orderedForManager(): List<CalendarSource> =
    sortedBy { it.isNotSynced }
