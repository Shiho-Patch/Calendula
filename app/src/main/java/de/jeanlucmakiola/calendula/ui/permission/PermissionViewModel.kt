package de.jeanlucmakiola.calendula.ui.permission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarVisibilityReconciler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PermissionViewModel @Inject constructor(
    private val visibilityReconciler: CalendarVisibilityReconciler,
) : ViewModel() {

    private val _state = MutableStateFlow<PermissionUiState>(PermissionUiState.Rationale)
    val state: StateFlow<PermissionUiState> = _state.asStateFlow()

    fun onGranted() {
        _state.value = PermissionUiState.Granted
        // The visibility reconcile needs the calendar permissions, so the launch
        // that started without them skipped it (#75) — this is the moment it can
        // finally run. A no-op on a fresh install, where nothing is pending.
        viewModelScope.launch { visibilityReconciler.run() }
    }

    fun onDenied() {
        _state.value = PermissionUiState.Denied
    }

    fun onRetry() {
        _state.value = PermissionUiState.Rationale
    }
}
