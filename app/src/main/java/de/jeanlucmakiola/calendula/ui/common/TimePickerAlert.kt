package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.jeanlucmakiola.calendula.R
import kotlinx.datetime.LocalTime

/**
 * M3 time picker in an alert dialog, seeded with [initial]. Shared by the event
 * form (start/end times) and Settings (the all-day reminder fire time).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerAlert(
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    // Honour the app's own time-format preference (the value every time label
    // reads), not the system/locale clock — otherwise an explicit 24h setting
    // still showed an AM/PM dial (issue #27).
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = LocalUse24HourFormat.current,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime(state.hour, state.minute)) }) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
        text = { TimePicker(state = state) },
    )
}
