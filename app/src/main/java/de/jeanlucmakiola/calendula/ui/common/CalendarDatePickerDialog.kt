package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.jeanlucmakiola.calendula.R
import kotlinx.datetime.LocalDate

/** One UTC day in milliseconds — the unit the M3 [DatePicker] speaks. */
const val MILLIS_PER_DAY: Long = 86_400_000L

/**
 * The app's standard Material 3 date picker, opened on [initial] and reporting
 * the chosen day through [onConfirm]. Shared by the event form (start/end date,
 * RRULE until) and the drawer's jump-to-date action.
 *
 * DatePicker speaks UTC-midnight millis; epoch-day arithmetic keeps the
 * conversion zone-proof in both directions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDatePickerDialog(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toEpochDays() * MILLIS_PER_DAY,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(LocalDate.fromEpochDays((millis / MILLIS_PER_DAY).toInt()))
                    }
                },
            ) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}
