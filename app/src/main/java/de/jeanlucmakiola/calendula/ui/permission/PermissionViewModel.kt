package de.jeanlucmakiola.calendula.ui.permission

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class PermissionViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow<PermissionUiState>(PermissionUiState.Rationale)
    val state: StateFlow<PermissionUiState> = _state.asStateFlow()

    // The visibility reconcile a grant owes (#75) hangs off RootScreen, which
    // also catches grants made outside the app.
    fun onGranted() {
        _state.value = PermissionUiState.Granted
    }

    fun onDenied() {
        _state.value = PermissionUiState.Denied
    }

    fun onRetry() {
        _state.value = PermissionUiState.Rationale
    }
}
