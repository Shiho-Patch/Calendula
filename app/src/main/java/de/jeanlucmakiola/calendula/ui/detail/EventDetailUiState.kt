package de.jeanlucmakiola.calendula.ui.detail

import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.FailureReason

/**
 * UI state for the event-detail bottom sheet (spec S4). Read-only in V1.
 */
sealed interface EventDetailUiState {
    data object Loading : EventDetailUiState
    data class Failure(val reason: FailureReason) : EventDetailUiState
    data class Success(
        val detail: EventDetail,
        /** Display name of the owning calendar, null if it can't be resolved. */
        val calendarName: String?,
    ) : EventDetailUiState
}
