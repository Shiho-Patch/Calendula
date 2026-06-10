package de.jeanlucmakiola.calendula.ui.filter

import de.jeanlucmakiola.calendula.domain.FailureReason

/**
 * State for the calendar-filter sheet (M3). The user toggles per-calendar
 * visibility; the choice is persisted app-side (separate from the system's
 * VISIBLE flag) and applied to every calendar view.
 */
sealed interface FilterUiState {
    data object Loading : FilterUiState
    data class Failure(val reason: FailureReason) : FilterUiState
    data class Success(val groups: List<AccountGroup>) : FilterUiState
}

/** Calendars grouped under the account that owns them (Nextcloud / Local / …). */
data class AccountGroup(
    val account: String,
    val calendars: List<CalendarRow>,
)

data class CalendarRow(
    val id: Long,
    val displayName: String,
    val color: Int,
    val visible: Boolean,
)
