package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.content.Context
import android.content.res.Resources
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
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
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = deviceUses24HourClock(LocalContext.current),
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

/**
 * Whether the clock should read 24-hour, matching the rest of the device.
 *
 * [DateFormat.is24HourFormat] resolves a "locale default" system setting against
 * the *app's* context locale — and this app applies a per-app language
 * (AppCompatDelegate), so an English UI on a German-region phone would wrongly
 * read 12-hour while the system clock shows 24-hour. So we honour an explicit
 * system 12/24 override, and otherwise fall back to the **device** locale
 * (Resources.getSystem), not the app's.
 */
private fun deviceUses24HourClock(context: Context): Boolean =
    when (Settings.System.getString(context.contentResolver, Settings.System.TIME_12_24)) {
        "24" -> true
        "12" -> false
        // 'a' is the AM/PM marker; a best-fit pattern without it is 24-hour.
        else -> {
            val deviceLocale = Resources.getSystem().configuration.locales[0]
            !DateFormat.getBestDateTimePattern(deviceLocale, "jm").contains('a')
        }
    }
