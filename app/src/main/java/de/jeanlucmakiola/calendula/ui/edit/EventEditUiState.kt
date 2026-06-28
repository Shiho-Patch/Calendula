package de.jeanlucmakiola.calendula.ui.edit

import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventColorOption
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventFormField
import de.jeanlucmakiola.calendula.domain.EventFormProblem
import de.jeanlucmakiola.calendula.domain.RecurringWriteScope

/**
 * UI state for the event form (v1.2: create; v1.3 reuses it for edit). Null
 * form means the screen hasn't been opened yet.
 */
data class EventEditUiState(
    /** The form with its calendar id resolved (picked > last used > first writable). */
    val form: EventForm,
    /** Calendars that accept writes — the only valid targets. */
    val calendars: List<CalendarSource>,
    /** Validation problems; empty until a save was attempted. */
    val problems: Set<EventFormProblem>,
    val saveState: SaveUiState,
    /** Optional sections currently rendered (settings defaults ∪ revealed). */
    val visibleFields: Set<EventFormField> = emptySet(),
    /**
     * Optional sections behind "more fields". Sections the current mode can't
     * offer at all (recurrence while editing a single occurrence) appear in
     * neither list.
     */
    val hiddenFields: List<EventFormField> = emptyList(),
    /** True while editing an existing event (the calendar is then fixed). */
    val isEditing: Boolean = false,
    /**
     * Whether to focus the title and raise the keyboard when this is a fresh
     * create form (issue #10). Mirrors the settings flag; the screen only acts
     * on it for a new event with an empty title, never when editing/importing.
     */
    val autofocusTitle: Boolean = true,
    /**
     * True while an edit changed the recurrence rule — the save-scope dialog
     * then drops "only this event" (an exception row can't carry a rule).
     */
    val recurrenceChanged: Boolean = false,
    /**
     * The event-colour palette the resolved target calendar publishes; empty
     * when it exposes none. Non-empty → the colour picker offers these swatches
     * (written as a key, sync-safe); empty → see [colorMode].
     */
    val colorPalette: List<EventColorOption> = emptyList(),
    /**
     * Whether the user has opted into custom colours on calendars that publish
     * no palette (a synced one may then drop the colour on sync). Mirrors the
     * settings flag; ignored for local and palette-backed calendars.
     */
    val allowColorOnUnsupportedCalendars: Boolean = false,
)

/** One-shot state of a save request, mirroring DeleteUiState on the detail screen. */
sealed interface SaveUiState {
    data object Idle : SaveUiState
    /** A dirty recurring event waits for the user to pick the write scope. */
    data object AwaitingScope : SaveUiState
    /**
     * The event changed externally (sync) while the form was open; the save
     * is parked with its chosen [scope] until the user picks overwrite,
     * discard, or cancel.
     */
    data class AwaitingConflict(val scope: RecurringWriteScope) : SaveUiState
    /** The event was deleted externally while the form was open. */
    data object Gone : SaveUiState
    data object Saving : SaveUiState
    data object Saved : SaveUiState
    /** WRITE_CALENDAR was revoked between the tap and the provider call. */
    data object NeedsPermission : SaveUiState
    data object Failed : SaveUiState
}
