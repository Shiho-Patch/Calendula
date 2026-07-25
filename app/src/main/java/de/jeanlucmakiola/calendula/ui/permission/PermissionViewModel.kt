package de.jeanlucmakiola.calendula.ui.permission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarVisibilityMigration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PermissionViewModel @Inject constructor(
    private val visibilityMigration: CalendarVisibilityMigration,
) : ViewModel() {

    private val _state = MutableStateFlow<PermissionUiState>(PermissionUiState.Rationale)
    val state: StateFlow<PermissionUiState> = _state.asStateFlow()

    fun onGranted() {
        _state.value = PermissionUiState.Granted
        // The one-shot visibility migration needs the calendar permissions, so a
        // launch that started without them left it pending (#75) — this is the
        // moment it can finally run. A no-op once it has, and on a fresh install
        // (nothing was ever disabled) it just stamps its guard.
        viewModelScope.launch { visibilityMigration.runIfNeeded() }
    }

    fun onDenied() {
        _state.value = PermissionUiState.Denied
    }

    fun onRetry() {
        _state.value = PermissionUiState.Rationale
    }
}
