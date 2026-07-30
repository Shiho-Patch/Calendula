package de.jeanlucmakiola.calendula.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.ui.common.CalendarColorChip
import de.jeanlucmakiola.calendula.ui.common.REMINDER_PRESETS
import de.jeanlucmakiola.calendula.ui.common.ReminderDefaultPicker
import de.jeanlucmakiola.calendula.ui.common.SnoozeDurationPicker
import de.jeanlucmakiola.calendula.ui.common.TimePickerAlert
import de.jeanlucmakiola.floret.components.CollapsingScaffold
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.floret.components.SelectedCheck
import de.jeanlucmakiola.floret.identity.collapseExit
import de.jeanlucmakiola.floret.identity.expandEnter
import de.jeanlucmakiola.floret.reminders.ReminderOverride
import de.jeanlucmakiola.floret.reminders.reminderOverrideFor
import kotlinx.datetime.LocalTime

/**
 * Reminder-notifications toggle (v1.4), mirroring the onboarding step.
 * Turning it on re-requests `POST_NOTIFICATIONS` when missing (API 33+) —
 * the pref is set either way; the OS permission is the real gate.
 */
@Composable
internal fun NotificationsScreen(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenSpecialDates: () -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* The pref is already on; a denial just leaves the OS gate shut. */ }
    val toggleReminders: (Boolean) -> Unit = { enabled ->
        viewModel.setRemindersEnabled(enabled)
        val needsPermission = enabled &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var showDefaultReminder by remember { mutableStateOf(false) }
    var showAllDayReminder by remember { mutableStateOf(false) }
    var showAllDayReminderTime by remember { mutableStateOf(false) }
    var showSnooze by remember { mutableStateOf(false) }
    var overrideDialog by remember { mutableStateOf<OverrideTarget?>(null) }
    var calendarSectionExpanded by remember { mutableStateOf(false) }
    var expandedCalendars by remember { mutableStateOf(emptySet<Long>()) }

    CollapsingScaffold(
        title = stringResource(R.string.settings_section_notifications),
        onBack = onBack,
        predictiveBack = true,
    ) {
        GroupedRow(
            title = stringResource(R.string.settings_reminders),
            summary = stringResource(R.string.settings_reminders_hint),
            position = Position.Top,
            trailing = {
                Switch(checked = state.remindersEnabled, onCheckedChange = toggleReminders)
            },
            onClick = { toggleReminders(!state.remindersEnabled) },
        )
        GroupedRow(
            title = stringResource(R.string.settings_default_reminder),
            summary = reminderChoiceLabel(state.defaultReminderMinutes),
            position = Position.Middle,
            onClick = { showDefaultReminder = true },
        )
        GroupedRow(
            title = stringResource(R.string.settings_default_reminder_allday),
            summary = reminderChoiceLabel(state.defaultAllDayReminderMinutes),
            position = Position.Middle,
            onClick = { showAllDayReminder = true },
        )
        GroupedRow(
            title = stringResource(R.string.settings_allday_reminder_time),
            summary = stringResource(
                R.string.settings_allday_reminder_time_hint,
                settingsTimeOfDay(state.allDayReminderTimeMinutes),
            ),
            position = Position.Bottom,
            onClick = { showAllDayReminderTime = true },
        )

        // Reliability is a soft, optional battery-optimisation exemption via
        // a system-settings deep-link, shown as live status.
        Spacer(Modifier.height(24.dp))
        val batteryExempt = rememberBatteryOptimizationExempt()
        GroupedRow(
            title = stringResource(R.string.settings_reliable_delivery),
            summary = if (batteryExempt) {
                stringResource(R.string.settings_reliable_delivery_exempt)
            } else {
                stringResource(R.string.settings_reliable_delivery_hint)
            },
            position = Position.Top,
            trailing = if (batteryExempt) {
                { SelectedCheck() }
            } else {
                null
            },
            onClick = { openBatteryOptimizationSettings(context) },
        )

        GroupedRow(
            title = stringResource(R.string.settings_snooze_duration),
            summary = snoozeDurationLabel(state.snoozeMinutes),
            position = Position.Bottom,
            onClick = { showSnooze = true },
        )

        // Per-calendar overrides, folded behind one header. Each writable
        // calendar may keep, drop or replace the global default, separately for
        // timed and all-day events.
        if (state.writableCalendars.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            GroupedRow(
                title = stringResource(R.string.settings_calendar_reminders_title),
                summary = stringResource(R.string.settings_calendar_reminders_hint),
                position = Position.Alone,
                trailing = {
                    Icon(
                        imageVector = if (calendarSectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = { calendarSectionExpanded = !calendarSectionExpanded },
            )
            AnimatedVisibility(
                visible = calendarSectionExpanded,
                enter = expandEnter(),
                exit = collapseExit(),
            ) {
                Column {
                    state.writableCalendars.forEach { calendar ->
                        Spacer(Modifier.height(16.dp))
                        // Special-dates calendars own their reminders in their
                        // own section — link there instead.
                        if (calendar.id in state.managedCalendarIds) {
                            GroupedRow(
                                title = calendar.displayName,
                                summary = stringResource(R.string.settings_calendar_reminders_managed_hint),
                                position = Position.Alone,
                                leading = { CalendarColorChip(calendar.color) },
                                trailing = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = onOpenSpecialDates,
                            )
                            return@forEach
                        }
                        val expanded = calendar.id in expandedCalendars
                        GroupedRow(
                            title = calendar.displayName,
                            position = if (expanded) Position.Top else Position.Alone,
                            leading = { CalendarColorChip(calendar.color) },
                            trailing = {
                                Icon(
                                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = {
                                expandedCalendars = if (expanded) {
                                    expandedCalendars - calendar.id
                                } else {
                                    expandedCalendars + calendar.id
                                }
                            },
                        )
                        AnimatedVisibility(
                            visible = expanded,
                            enter = expandEnter(),
                            exit = collapseExit(),
                        ) {
                            Column {
                                val timed = state.perCalendarReminderOverride.reminderOverrideFor(calendar.id)
                                GroupedRow(
                                    title = stringResource(R.string.settings_default_reminder),
                                    summary = calendarOverrideSummary(timed, state.defaultReminderMinutes),
                                    position = Position.Middle,
                                    onClick = { overrideDialog = OverrideTarget(calendar.id, isAllDay = false) },
                                )
                                val allDay = state.perCalendarAllDayReminderOverride.reminderOverrideFor(calendar.id)
                                GroupedRow(
                                    title = stringResource(R.string.settings_default_reminder_allday),
                                    summary = calendarOverrideSummary(allDay, state.defaultAllDayReminderMinutes),
                                    position = Position.Bottom,
                                    onClick = { overrideDialog = OverrideTarget(calendar.id, isAllDay = true) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSnooze) {
        SnoozeDurationPicker(
            title = stringResource(R.string.settings_snooze_duration),
            presets = SNOOZE_PRESETS,
            selected = state.snoozeMinutes,
            label = { snoozeDurationLabel(it) },
            onSelect = { viewModel.setSnoozeMinutes(it) },
            onDismiss = { showSnooze = false },
        )
    }
    if (showDefaultReminder) {
        ReminderDefaultPicker(
            title = stringResource(R.string.settings_default_reminder),
            presets = REMINDER_PRESETS,
            selected = state.defaultReminderMinutes.toReminderChoice(),
            allowInherit = false,
            onSelect = { viewModel.setDefaultReminderMinutes(it.toMinutesList()) },
            onDismiss = { showDefaultReminder = false },
        )
    }
    if (showAllDayReminder) {
        ReminderDefaultPicker(
            title = stringResource(R.string.settings_default_reminder_allday),
            presets = ALLDAY_REMINDER_PRESETS,
            selected = state.defaultAllDayReminderMinutes.toReminderChoice(),
            allowInherit = false,
            onSelect = { viewModel.setDefaultAllDayReminderMinutes(it.toMinutesList()) },
            onDismiss = { showAllDayReminder = false },
            leadTimeSummary = allDayFiringTimeSummary(state.allDayReminderTimeMinutes),
        )
    }
    if (showAllDayReminderTime) {
        TimePickerAlert(
            initial = LocalTime(
                state.allDayReminderTimeMinutes / 60,
                state.allDayReminderTimeMinutes % 60,
            ),
            onConfirm = {
                viewModel.setAllDayReminderTimeMinutes(it.hour * 60 + it.minute)
                showAllDayReminderTime = false
            },
            onDismiss = { showAllDayReminderTime = false },
        )
    }
    overrideDialog?.let { target ->
        val map = if (target.isAllDay) {
            state.perCalendarAllDayReminderOverride
        } else {
            state.perCalendarReminderOverride
        }
        ReminderDefaultPicker(
            title = stringResource(
                if (target.isAllDay) {
                    R.string.settings_default_reminder_allday
                } else {
                    R.string.settings_default_reminder
                },
            ),
            presets = if (target.isAllDay) ALLDAY_REMINDER_PRESETS else REMINDER_PRESETS,
            selected = map.reminderOverrideFor(target.calendarId),
            allowInherit = true,
            onSelect = {
                if (target.isAllDay) {
                    viewModel.setCalendarAllDayReminderOverride(target.calendarId, it)
                } else {
                    viewModel.setCalendarReminderOverride(target.calendarId, it)
                }
            },
            onDismiss = { overrideDialog = null },
            leadTimeSummary = if (target.isAllDay) {
                allDayFiringTimeSummary(state.allDayReminderTimeMinutes)
            } else {
                null
            },
        )
    }
}

/** Which calendar + event kind a per-calendar reminder-override dialog targets. */
private data class OverrideTarget(val calendarId: Long, val isAllDay: Boolean)

/** A global default (empty = none) as a picker choice for selection highlighting. */
private fun List<Int>.toReminderChoice(): ReminderOverride =
    if (isEmpty()) ReminderOverride.None else ReminderOverride.Minutes(this)

/** A picked choice as global-default minutes (Inherit isn't offered for globals). */
private fun ReminderOverride.toMinutesList(): List<Int> =
    (this as? ReminderOverride.Minutes)?.minutes ?: emptyList()

/** Row summary for a calendar: its override, or the inherited global default. */
@Composable
private fun calendarOverrideSummary(
    choice: ReminderOverride,
    globalDefault: List<Int>,
): String = when (choice) {
    ReminderOverride.Inherit ->
        stringResource(R.string.settings_calendar_reminder_inherits, reminderChoiceLabel(globalDefault))
    ReminderOverride.None -> stringResource(R.string.reminder_none)
    is ReminderOverride.Minutes -> reminderChoiceLabel(choice.minutes)
}

/** Snooze delays offered for the notification "Snooze" action, in minutes. */
private val SNOOZE_PRESETS = listOf(5, 10, 15, 30, 60)

/** A snooze delay as a plain duration ("10 minutes", "1 hour") — no "before". */
@Composable
private fun snoozeDurationLabel(minutes: Int): String =
    if (minutes % 60 == 0) {
        pluralStringResource(R.plurals.duration_hours, minutes / 60, minutes / 60)
    } else {
        pluralStringResource(R.plurals.duration_minutes, minutes, minutes)
    }

/**
 * Whether Calendula is exempt from battery optimisation, re-read on every
 * `ON_RESUME` so a change made in system settings shows up at once.
 */
@Composable
private fun rememberBatteryOptimizationExempt(): Boolean {
    val context = LocalContext.current
    var exempt by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exempt = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return exempt
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return power.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Open the direct `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` dialog, falling back to
 * the optimisation list if the OS refuses it.
 */
private fun openBatteryOptimizationSettings(context: Context) {
    val direct = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        "package:${context.packageName}".toUri(),
    )
    if (runCatching { context.startActivity(direct) }.isFailure) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
