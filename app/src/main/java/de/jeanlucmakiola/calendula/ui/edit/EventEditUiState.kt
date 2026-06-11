package de.jeanlucmakiola.calendula.ui.edit

import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventFormField
import de.jeanlucmakiola.calendula.domain.EventFormProblem

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
    /** True while at least one optional section hides behind "more fields". */
    val hasHiddenFields: Boolean = false,
)

/** One-shot state of a save request, mirroring DeleteUiState on the detail screen. */
sealed interface SaveUiState {
    data object Idle : SaveUiState
    data object Saving : SaveUiState
    data object Saved : SaveUiState
    /** WRITE_CALENDAR was revoked between the tap and the provider call. */
    data object NeedsPermission : SaveUiState
    data object Failed : SaveUiState
}
