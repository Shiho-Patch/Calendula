package de.jeanlucmakiola.calendula.ui.calendars

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.calendar.CalendarVisibilityReconciler
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The one-time notice that Calendula now follows the device's per-calendar
 * visibility (#75), armed by `CalendarVisibilityReconciler`. The app does not
 * switch those calendars back on — that would un-hide them everywhere else too.
 */
@HiltViewModel
class CalendarVisibilityNoticeViewModel @Inject constructor(
    private val prefs: CalendarPrefs,
    private val reconciler: CalendarVisibilityReconciler,
) : ViewModel() {

    /**
     * Reconcile whenever the app comes up with the calendar permission held,
     * rather than off one grant route: a permission granted on Android's
     * app-settings screen never reaches the permission screen's callback.
     */
    fun reconcile() {
        viewModelScope.launch { reconciler.run() }
    }

    val pending: StateFlow<Boolean> = prefs.visibilityNoticePending
        .map { it == true }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = false,
        )

    fun dismiss() {
        viewModelScope.launch { prefs.setVisibilityNoticePending(false) }
    }
}

/** Plain informational dialog — one acknowledgement, nothing to decide. */
@Composable
fun CalendarVisibilityNoticeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
        title = { Text(stringResource(R.string.calendars_visibility_notice_title)) },
        text = { Text(stringResource(R.string.calendars_visibility_notice_message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
    )
}
