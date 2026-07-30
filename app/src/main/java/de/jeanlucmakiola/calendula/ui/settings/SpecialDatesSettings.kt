package de.jeanlucmakiola.calendula.ui.settings

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.contacts.hasContactsPermission
import de.jeanlucmakiola.calendula.domain.contacts.SpecialDateType
import de.jeanlucmakiola.calendula.ui.common.ReminderDefaultPicker
import de.jeanlucmakiola.floret.components.CollapsingScaffold
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.InlineTextField
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.floret.reminders.ReminderOverride

// ---------------------------------------------------------------------------
// Contact special dates (issue #15)
// ---------------------------------------------------------------------------

@Composable
internal fun SpecialDatesScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.specialDatesState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // READ_CONTACTS is requested only here, on enable — never at startup. On a
    // grant we either enable (if turning on) or just re-sync (clearing a stalled
    // banner after the permission was re-granted).
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            if (!state.enabled) viewModel.setSpecialDatesEnabled(true) else viewModel.syncSpecialDatesNow()
        }
    }
    val requestOrEnable: () -> Unit = {
        if (context.hasContactsPermission()) {
            viewModel.setSpecialDatesEnabled(true)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    var confirmDisableAll by remember { mutableStateOf(false) }
    var confirmDisableType by remember { mutableStateOf<SpecialDateType?>(null) }
    var editTemplate by remember { mutableStateOf<SpecialDateType?>(null) }
    var reminderPickerType by remember { mutableStateOf<SpecialDateType?>(null) }

    CollapsingScaffold(
        title = stringResource(R.string.settings_section_special_dates),
        onBack = onBack,
        predictiveBack = true,
    ) {
        // Paused banner: the permission was revoked after enabling.
        if (state.enabled && state.stalledPermission) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_special_dates_paused_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = stringResource(R.string.settings_special_dates_paused_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                    ) { Text(stringResource(R.string.settings_special_dates_grant)) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        GroupedRow(
            title = stringResource(R.string.settings_special_dates_enable),
            summary = stringResource(R.string.settings_special_dates_enable_hint),
            position = Position.Alone,
            trailing = {
                Switch(
                    checked = state.enabled,
                    onCheckedChange = { want -> if (want) requestOrEnable() else confirmDisableAll = true },
                )
            },
            onClick = { if (state.enabled) confirmDisableAll = true else requestOrEnable() },
        )

        if (state.enabled) {
            // Per-type card: the toggle, and while on, its title format and its
            // calendar-wide reminder.
            SpecialDateType.entries.forEachIndexed { index, type ->
                Spacer(Modifier.height(if (index == 0) 24.dp else 16.dp))
                val on = type in state.types
                GroupedRow(
                    title = stringResource(specialDateTypeLabel(type)),
                    position = if (on) Position.Top else Position.Alone,
                    trailing = {
                        Switch(
                            checked = on,
                            onCheckedChange = { want ->
                                if (want) viewModel.setSpecialDateTypeEnabled(type, true)
                                else confirmDisableType = type
                            },
                        )
                    },
                    onClick = {
                        if (on) confirmDisableType = type else viewModel.setSpecialDateTypeEnabled(type, true)
                    },
                )
                if (on) {
                    GroupedRow(
                        title = stringResource(R.string.settings_special_dates_template),
                        summary = state.titleTemplates[type].orEmpty(),
                        position = Position.Middle,
                        onClick = { editTemplate = type },
                    )
                    GroupedRow(
                        title = stringResource(R.string.settings_special_dates_reminders),
                        summary = reminderChoiceLabel(specialDatesReminderMinutes(state.reminderChoices[type])),
                        position = Position.Bottom,
                        onClick = { reminderPickerType = type },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            GroupedRow(
                title = stringResource(R.string.settings_special_dates_show_year),
                summary = stringResource(R.string.settings_special_dates_show_year_hint),
                position = Position.Top,
                trailing = {
                    Switch(
                        checked = state.showYear,
                        onCheckedChange = viewModel::setSpecialDatesShowYear,
                    )
                },
                onClick = { viewModel.setSpecialDatesShowYear(!state.showYear) },
            )
            GroupedRow(
                title = stringResource(R.string.settings_special_dates_sync_now),
                summary = specialDatesLastRunLabel(context, state.lastRun),
                position = Position.Bottom,
                onClick = viewModel::syncSpecialDatesNow,
            )

            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.settings_special_dates_calendar_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }

    if (confirmDisableAll) {
        SpecialDatesDisableDialog(
            message = stringResource(R.string.settings_special_dates_disable_all_message),
            onConfirm = { viewModel.setSpecialDatesEnabled(false); confirmDisableAll = false },
            onDismiss = { confirmDisableAll = false },
        )
    }
    confirmDisableType?.let { type ->
        SpecialDatesDisableDialog(
            message = stringResource(
                R.string.settings_special_dates_disable_type_message,
                stringResource(specialDateTypeLabel(type)),
            ),
            onConfirm = { viewModel.setSpecialDateTypeEnabled(type, false); confirmDisableType = null },
            onDismiss = { confirmDisableType = null },
        )
    }
    editTemplate?.let { type ->
        SpecialDatesTemplateDialog(
            initial = state.titleTemplates[type].orEmpty(),
            onConfirm = { viewModel.setSpecialDatesTitleTemplate(type, it); editTemplate = null },
            onDismiss = { editTemplate = null },
        )
    }
    reminderPickerType?.let { type ->
        ReminderDefaultPicker(
            title = stringResource(R.string.settings_special_dates_reminders),
            presets = ALLDAY_REMINDER_PRESETS,
            selected = state.reminderChoices[type] ?: ReminderOverride.None,
            // Managed calendars own their reminders outright — no "inherit global".
            allowInherit = false,
            onSelect = { viewModel.setSpecialDatesReminders(type, it) },
            onDismiss = { reminderPickerType = null },
        )
    }
}

/** The lead-time list backing a managed calendar's reminder choice (for the summary label). */
private fun specialDatesReminderMinutes(choice: ReminderOverride?): List<Int> =
    (choice as? ReminderOverride.Minutes)?.minutes.orEmpty()

@Composable
private fun SpecialDatesDisableDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_special_dates_disable_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_special_dates_disable_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

@Composable
private fun SpecialDatesTemplateDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_special_dates_template)) },
        text = {
            Column {
                // The app's borderless input over a tonal surface (the dialog
                // convention — see DialogControls), not Material's outlined field.
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    InlineTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = stringResource(R.string.settings_special_dates_template),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_special_dates_template_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.dialog_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}

private fun specialDateTypeLabel(type: SpecialDateType): Int = when (type) {
    SpecialDateType.Birthday -> R.string.settings_special_dates_type_birthday
    SpecialDateType.Anniversary -> R.string.settings_special_dates_type_anniversary
    SpecialDateType.Custom -> R.string.settings_special_dates_type_custom
}

@Composable
private fun specialDatesLastRunLabel(context: Context, lastRun: Long): String =
    if (lastRun <= 0L) {
        stringResource(R.string.settings_special_dates_never_synced)
    } else {
        stringResource(
            R.string.settings_special_dates_last_synced,
            android.text.format.DateUtils.getRelativeTimeSpanString(
                lastRun,
                System.currentTimeMillis(),
                android.text.format.DateUtils.MINUTE_IN_MILLIS,
            ).toString(),
        )
    }
