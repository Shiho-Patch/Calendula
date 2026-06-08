package de.jeanlucmakiola.calendula.ui.debug

import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.FailureReason

sealed interface DebugUiState {
    data object Loading : DebugUiState
    data class Failure(val reason: FailureReason) : DebugUiState
    data class Success(
        val calendars: List<CalendarSource>,
        val nextEvents: List<EventInstance>,
    ) : DebugUiState
}
