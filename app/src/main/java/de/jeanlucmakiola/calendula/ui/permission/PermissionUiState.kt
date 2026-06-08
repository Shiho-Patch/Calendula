package de.jeanlucmakiola.calendula.ui.permission

sealed interface PermissionUiState {
    data object Rationale : PermissionUiState
    data object Denied : PermissionUiState
    data object Granted : PermissionUiState
}
