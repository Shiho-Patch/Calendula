package de.jeanlucmakiola.calendula.ui.detail

import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.FailureReason

/**
 * UI state for the event-detail screen (spec S4).
 */
sealed interface EventDetailUiState {
    data object Loading : EventDetailUiState
    data class Failure(val reason: FailureReason) : EventDetailUiState
    data class Success(
        val detail: EventDetail,
        /** Display name of the owning calendar, null if it can't be resolved. */
        val calendarName: String?,
        /** Whether the owning calendar allows modifying events (shows edit/delete). */
        val canModify: Boolean = false,
    ) : EventDetailUiState
}

/**
 * One-shot state of a delete request, separate from the screen state so a
 * failed delete leaves the loaded detail visible.
 */
sealed interface DeleteUiState {
    data object Idle : DeleteUiState
    data object Deleting : DeleteUiState
    data object Deleted : DeleteUiState
    /** WRITE_CALENDAR was revoked between the tap and the provider call. */
    data object NeedsPermission : DeleteUiState
    data object Failed : DeleteUiState
}
