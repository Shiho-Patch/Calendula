package de.jeanlucmakiola.calendula.domain

/**
 * The ways a calendar can behave unlike a plain, writable one — each a reason it
 * is missing from the event and import pickers (#76).
 */
enum class CalendarStateLabel {
    /**
     * A special-dates mirror the app fills from contacts. Writable and visible,
     * yet no event target: anything authored here is deleted by the next sync.
     */
    MANAGED,

    /** Contents can't be modified: a WebCal subscription, a read-only share. */
    READ_ONLY,

    /** The account holds the events, but this device isn't syncing them down. */
    NOT_SYNCED,
}

/**
 * Whether the account keeps this calendar's events off the device
 * (`Calendars.SYNC_EVENTS = 0`) — empty by construction. Device-local calendars
 * are excluded: nothing syncs them by definition, and one from another app can
 * hold real events at `sync_events = 0`.
 */
val CalendarSource.isNotSynced: Boolean
    get() = !syncsEvents && !isLocal

/**
 * Whether a visibility switch on this calendar can change anything the user
 * would see — it can't for a non-syncing one, with no events on the device.
 */
val CalendarSource.hasVisibilitySwitch: Boolean
    get() = !isNotSynced

/**
 * Whether this calendar can be offered as a target for a new or imported event.
 * The one predicate behind both pickers, so the states [CalendarStateLabel]
 * names on a manager row are exactly the states that keep a calendar out of them
 * (#76). An event already living in an excluded calendar keeps it; the editor
 * adds that calendar back to its picker.
 */
val CalendarSource.isEventTarget: Boolean
    get() = canModifyContents && isVisibleInSystem && !isManaged && !isNotSynced

/**
 * Whether this calendar's events may have their times rewritten by a drag (#68).
 * Deliberately not [isEventTarget]: a managed event stays editable (reminders,
 * notes) yet must never move, since the next contacts sync would put it back.
 */
val CalendarSource.allowsEventMove: Boolean
    get() = canModifyContents && !isManaged

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
