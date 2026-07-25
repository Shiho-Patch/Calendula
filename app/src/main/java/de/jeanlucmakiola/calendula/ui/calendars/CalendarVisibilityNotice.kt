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
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The one-time notice that Calendula now follows the device's per-calendar
 * visibility (#75). Armed by `CalendarVisibilityReconciler` on the first launch
 * that finds a calendar switched off outside the app — those used to show their
 * events here and no longer do, and the app deliberately does not switch them
 * back on, because that would un-hide them in every other calendar app too.
 */
@HiltViewModel
class CalendarVisibilityNoticeViewModel @Inject constructor(
    private val prefs: CalendarPrefs,
) : ViewModel() {

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
