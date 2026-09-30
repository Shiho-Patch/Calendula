package de.jeanlucmakiola.calendula.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.domain.EventFormField
import de.jeanlucmakiola.calendula.ui.common.CalendarColorChip
import de.jeanlucmakiola.calendula.ui.common.DurationPicker
import de.jeanlucmakiola.calendula.ui.common.durationLabel
import de.jeanlucmakiola.calendula.ui.common.eventFormFieldIcon
import de.jeanlucmakiola.calendula.ui.common.eventFormFieldLabel
import de.jeanlucmakiola.floret.components.CollapsingScaffold
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.floret.components.positionOf
import de.jeanlucmakiola.floret.identity.collapseExit
import de.jeanlucmakiola.floret.identity.expandEnter

/** New event form: which fields it opens with, and how it behaves. */
@Composable
internal fun EventFormScreen(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    visible: Boolean = true,
) {
    var showDefaultDuration by remember { mutableStateOf(false) }
    // The calendar whose duration-override picker is open, if any.
    var durationTarget by remember { mutableStateOf<Long?>(null) }
    var durationSectionExpanded by remember { mutableStateOf(false) }
    // Special-dates calendars carry all-day events only, which have no length.
    val durationCalendars = state.writableCalendars.filterNot { it.id in state.managedCalendarIds }

    CollapsingScaffold(
        title = stringResource(R.string.settings_section_event_form),
        onBack = onBack,
        predictiveBack = true,
        visible = visible,
    ) {
        SettingsHint(stringResource(R.string.settings_form_fields_hint))
        Spacer(Modifier.height(8.dp))
        val fields = EventFormField.entries
        fields.forEachIndexed { index, field ->
            val checked = field in state.defaultFormFields
            GroupedRow(
                title = stringResource(eventFormFieldLabel(field)),
                position = positionOf(index, fields.size),
                // The same icon the field carries in the new-event form.
                leading = {
                    Icon(
                        imageVector = eventFormFieldIcon(field),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailing = {
                    Switch(
                        checked = checked,
                        onCheckedChange = { viewModel.setFormFieldDefault(field, it) },
                    )
                },
                onClick = { viewModel.setFormFieldDefault(field, !checked) },
            )
        }

        // Auto-focus the title on a new event (#10), on by default.
        Spacer(Modifier.height(24.dp))
        GroupedRow(
            title = stringResource(R.string.settings_autofocus_title),
            summary = stringResource(R.string.settings_autofocus_title_hint),
            position = Position.Alone,
            leading = {
                Icon(
                    imageVector = Icons.Default.Keyboard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailing = {
                Switch(
                    checked = state.autofocusEventTitle,
                    onCheckedChange = { viewModel.setAutofocusEventTitle(it) },
                )
            },
            onClick = { viewModel.setAutofocusEventTitle(!state.autofocusEventTitle) },
        )

        // How long a new timed event opens (#54), globally and per calendar —
        // the per-calendar list folds behind its own header, like the reminders.
        Spacer(Modifier.height(24.dp))
        GroupedRow(
            title = stringResource(R.string.settings_event_duration),
            summary = durationLabel(state.defaultEventDurationMinutes),
            position = if (durationCalendars.isEmpty()) Position.Alone else Position.Top,
            leading = {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = { showDefaultDuration = true },
        )
        if (durationCalendars.isNotEmpty()) {
            GroupedRow(
                title = stringResource(R.string.settings_calendar_durations_title),
                summary = stringResource(R.string.settings_calendar_durations_hint),
                // Expanded, the header opens the calendar run below it and the
                // two read as one container (the shared expand pattern).
                position = if (durationSectionExpanded) Position.Top else Position.Bottom,
                trailing = {
                    Icon(
                        imageVector = if (durationSectionExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = { durationSectionExpanded = !durationSectionExpanded },
            )
            AnimatedVisibility(
                visible = durationSectionExpanded,
                enter = expandEnter(),
                exit = collapseExit(),
            ) {
                Column {
                    durationCalendars.forEachIndexed { index, calendar ->
                        val override = state.perCalendarEventDuration[calendar.id]
                        GroupedRow(
                            title = calendar.displayName.ifBlank { stringResource(R.string.calendar_unnamed) },
                            summary = override?.let { durationLabel(it) }
                                ?: stringResource(
                                    R.string.settings_calendar_duration_inherits,
                                    durationLabel(state.defaultEventDurationMinutes),
                                ),
                            // The header above is the run's first row, so the
                            // calendars continue it: middles, then its bottom.
                            position = positionOf(index + 1, durationCalendars.size + 1),
                            leading = { CalendarColorChip(calendar.color) },
                            onClick = { durationTarget = calendar.id },
                        )
                    }
                }
            }
        }

        // Per-event colour on calendars that publish no colour set (some
        // CalDAV); off by default, since it may not survive their next sync.
        Spacer(Modifier.height(24.dp))
        GroupedRow(
            title = stringResource(R.string.settings_color_unsupported),
            summary = stringResource(R.string.settings_color_unsupported_hint),
            position = Position.Alone,
            trailing = {
                Switch(
                    checked = state.allowColorOnUnsupportedCalendars,
                    onCheckedChange = { viewModel.setAllowColorOnUnsupportedCalendars(it) },
                )
            },
            onClick = {
                viewModel.setAllowColorOnUnsupportedCalendars(
                    !state.allowColorOnUnsupportedCalendars,
                )
            },
        )
    }

    if (showDefaultDuration) {
        DurationPicker(
            title = stringResource(R.string.settings_event_duration),
            description = stringResource(R.string.settings_event_duration_hint),
            presets = EVENT_DURATION_PRESETS,
            selected = state.defaultEventDurationMinutes,
            label = { durationLabel(it) },
            // No inherit row on the global default, so a pick is never null.
            onSelect = { minutes -> minutes?.let { viewModel.setDefaultEventDuration(it) } },
            onDismiss = { showDefaultDuration = false },
            maxMinutes = SettingsPrefs.MAX_EVENT_DURATION,
        )
    }
    durationTarget?.let { calendarId ->
        DurationPicker(
            title = stringResource(R.string.settings_event_duration),
            presets = EVENT_DURATION_PRESETS,
            selected = state.perCalendarEventDuration[calendarId],
            label = { durationLabel(it) },
            inheritLabel = stringResource(
                R.string.settings_calendar_duration_use_default,
                durationLabel(state.defaultEventDurationMinutes),
            ),
            onSelect = { viewModel.setCalendarEventDuration(calendarId, it) },
            onDismiss = { durationTarget = null },
            maxMinutes = SettingsPrefs.MAX_EVENT_DURATION,
        )
    }
}

/** Lengths offered for a new timed event, in minutes — up to a full work day. */
private val EVENT_DURATION_PRESETS = listOf(15, 30, 45, 60, 90, 120, 240, 480)
