package de.jeanlucmakiola.calendula.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.calendar.MINUTES_DEFAULT
import de.jeanlucmakiola.floret.reminders.ReminderUnit

/** Common reminder lead times offered as quick picks in the form and settings. */
val REMINDER_PRESETS = listOf(0, 10, 30, 60, 1_440)

@StringRes
fun reminderUnitLabel(unit: ReminderUnit): Int = when (unit) {
    ReminderUnit.Minutes -> R.string.reminder_unit_minutes
    ReminderUnit.Hours -> R.string.reminder_unit_hours
    ReminderUnit.Days -> R.string.reminder_unit_days
    ReminderUnit.Weeks -> R.string.reminder_unit_weeks
}

/**
 * Humanise a reminder lead time (minutes before the event start) into one
 * line: "Default reminder" ([MINUTES_DEFAULT]), "At time of event" (0),
 * "10 minutes before", "1 hour before", … Shared by the detail screen, the event
 * form and the default-reminder settings so the wording never drifts.
 *
 * Only the sentinel itself is the account default. Any other negative is a real
 * lead time firing *after* the start — rare, but a foreign all-day row decodes to
 * one — and naming it "Default reminder" would tell the user an alarm that does
 * go off is not ours.
 */
@Composable
fun reminderLeadTimeLabel(minutes: Int): String = when {
    minutes == MINUTES_DEFAULT -> stringResource(R.string.reminder_default)
    minutes == 0 -> stringResource(R.string.reminder_at_time)
    minutes < 0 -> stringResource(R.string.reminder_after, durationLabel(-minutes))
    minutes % 10_080 == 0 ->
        pluralStringResource(R.plurals.reminder_weeks, minutes / 10_080, minutes / 10_080)
    minutes % 1_440 == 0 ->
        pluralStringResource(R.plurals.reminder_days, minutes / 1_440, minutes / 1_440)
    minutes % 60 == 0 ->
        pluralStringResource(R.plurals.reminder_hours, minutes / 60, minutes / 60)
    else -> pluralStringResource(R.plurals.reminder_minutes, minutes, minutes)
}

/**
 * Humanise a plain duration — no "before": "45 minutes", "8 hours", or both
 * parts for a mixed length ("1 hour 30 minutes"). Shared by the snooze delay and
 * the default event duration (#54).
 */
@Composable
fun durationLabel(minutes: Int): String {
    val hours = minutes / 60
    val rest = minutes % 60
    val hoursLabel = pluralStringResource(R.plurals.duration_hours, hours, hours)
    val minutesLabel = pluralStringResource(R.plurals.duration_minutes, rest, rest)
    return when {
        hours == 0 -> minutesLabel
        rest == 0 -> hoursLabel
        else -> stringResource(R.string.duration_hours_minutes, hoursLabel, minutesLabel)
    }
}
