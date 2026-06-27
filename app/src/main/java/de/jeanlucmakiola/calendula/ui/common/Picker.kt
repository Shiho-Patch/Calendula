package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.CalendarReminderOverride
import de.jeanlucmakiola.calendula.ui.agenda.AgendaRange

/**
 * Shared full-screen scaffold for selection pickers: a full-bleed [Dialog] that
 * reuses the app's [CollapsingScaffold] (collapsing title + back button), so a
 * picker is visually identical to a Settings sub-page and uses the full width.
 * [content] places the connected grouped rows; selecting one calls [onDismiss].
 */
@Composable
fun FullScreenPicker(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // The dialog window pans by default when the keyboard opens, which —
        // combined with the content's own imePadding — leaves a fixed black gap
        // above the keyboard. Switch it to ADJUST_NOTHING so the window stays
        // full-screen and imePadding alone lifts the focused field.
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window
                ?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        CollapsingScaffold(title = title, onBack = onDismiss, content = content)
    }
}

/**
 * General single-select picker, full-screen: each option is a connected grouped
 * row and the current one carries a check. Drop-in for the former dialog
 * (theme, week start, language, …).
 */
@Composable
fun <T> OptionPicker(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    FullScreenPicker(title = title, onDismiss = onDismiss) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            GroupedRow(
                title = label(option),
                position = positionOf(index, options.size),
                selected = isSelected,
                trailing = if (isSelected) {
                    { SelectedCheck() }
                } else {
                    null
                },
                onClick = {
                    onSelect(option)
                    onDismiss()
                },
            )
        }
    }
}

/**
 * Reminder-default picker, full-screen: the grouped list (with an optional "Use
 * default reminder" row and a "None" row), the [presets] as lead-time rows, and
 * a "Custom" row that expands an inline number field plus a segmented unit
 * selector. Returns the choice as a [CalendarReminderOverride].
 */
@Composable
fun ReminderDefaultPicker(
    title: String,
    presets: List<Int>,
    selected: CalendarReminderOverride,
    allowInherit: Boolean,
    onSelect: (CalendarReminderOverride) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedMinutes = (selected as? CalendarReminderOverride.Minutes)?.minutes
    val customSelected = selectedMinutes != null && selectedMinutes !in presets
    val seed = decomposeReminder(selectedMinutes?.takeIf { customSelected })

    var customExpanded by rememberSaveable { mutableStateOf(false) }
    var amountText by rememberSaveable { mutableStateOf(seed.first) }
    var unit by rememberSaveable { mutableStateOf(seed.second) }

    val options = buildList {
        if (allowInherit) add(CalendarReminderOverride.Inherit)
        add(CalendarReminderOverride.None)
        presets.forEach { add(CalendarReminderOverride.Minutes(it)) }
    }
    val rowCount = options.size + 1 // + the custom row

    FullScreenPicker(title = title, onDismiss = onDismiss) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            GroupedRow(
                title = reminderOverrideLabel(option),
                position = positionOf(index, rowCount),
                selected = isSelected,
                trailing = if (isSelected) {
                    { SelectedCheck() }
                } else {
                    null
                },
                onClick = {
                    onSelect(option)
                    onDismiss()
                },
            )
        }
        // When expanded, the Custom row connects downward into the editor card
        // so the two read as one grouped container (the per-calendar pattern).
        GroupedRow(
            title = if (customSelected) {
                stringResource(
                    R.string.reminder_custom_with_value,
                    reminderLeadTimeLabel(selectedMinutes!!),
                )
            } else {
                stringResource(R.string.event_edit_reminder_custom)
            },
            position = if (customExpanded) Position.Top else positionOf(options.size, rowCount),
            selected = customSelected,
            trailing = if (customSelected) {
                { SelectedCheck() }
            } else {
                null
            },
            onClick = { customExpanded = !customExpanded },
        )
        AnimatedVisibility(
            visible = customExpanded,
            enter = calendarExpandEnter(),
            exit = calendarCollapseExit(),
        ) {
            CustomReminderEditor(
                amountText = amountText,
                onAmountChange = { amountText = it },
                unit = unit,
                onUnitChange = { unit = it },
                onConfirm = { minutes ->
                    onSelect(CalendarReminderOverride.Minutes(minutes))
                    onDismiss()
                },
            )
        }
    }
}

/**
 * The expanded "Custom" lead-time editor: a tonal card connected to the Custom
 * row above it (matching the grouped-row system, so the two read as one
 * container). An amount field with a live preview of the resulting lead time, a
 * single-choice unit toggle, and a tonal confirm enabled only for a valid
 * 1–999 amount. [onConfirm] receives the final lead time in minutes.
 */
@Composable
private fun CustomReminderEditor(
    amountText: String,
    onAmountChange: (String) -> Unit,
    unit: ReminderUnit,
    onUnitChange: (ReminderUnit) -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val amount = amountText.toIntOrNull()?.takeIf { it in 1..999 }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        // A Position.Bottom shape: tight top corners meeting the row, full bottom.
        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 22.dp, bottomEnd = 22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Unit toggle first so it stays visible above the keyboard once the
            // amount field (the bottom row) is focused and scrolled into view.
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ReminderUnit.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = unit == entry,
                        onClick = { onUnitChange(entry) },
                        shape = SegmentedButtonDefaults.itemShape(index, ReminderUnit.entries.size),
                        label = { Text(stringResource(reminderUnitLabel(entry))) },
                    )
                }
            }
            // Amount, a live preview of the lead time it resolves to, and Set —
            // all on one row, sitting just above the keyboard.
            Row(verticalAlignment = Alignment.CenterVertically) {
                DialogAmountField(
                    value = amountText,
                    onValueChange = onAmountChange,
                    placeholder = "10",
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = amount?.let { reminderLeadTimeLabel(it * unit.minutesFactor) }
                        ?: stringResource(R.string.reminder_custom_amount),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(16.dp))
                FilledTonalButton(
                    onClick = { amount?.let { onConfirm(it * unit.minutesFactor) } },
                    enabled = amount != null,
                ) {
                    Text(stringResource(R.string.reminder_custom_set))
                }
            }
        }
    }
}

/** A short explanatory paragraph shown under a picker's title, above the rows. */
@Composable
private fun PickerDescription(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SelectedCheck() {
    Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
    )
}

/**
 * Agenda-range picker, full-screen: the fixed 1 day / 1 week / 1 month windows
 * as grouped rows, plus a "Custom" row that expands an inline day-count editor
 * (1–365). Mirrors [ReminderDefaultPicker]'s custom-expand pattern.
 */
@Composable
fun AgendaRangePicker(
    title: String,
    description: String,
    selected: AgendaRange,
    onSelect: (AgendaRange) -> Unit,
    onDismiss: () -> Unit,
) {
    val presets = listOf(
        AgendaRange.Day,
        AgendaRange.ThisWeek,
        AgendaRange.ThisMonth,
        AgendaRange.Week,
        AgendaRange.Month,
    )
    val customSelected = selected is AgendaRange.Custom
    val rowCount = presets.size + 1 // + the custom row

    var customExpanded by rememberSaveable { mutableStateOf(false) }
    var amountText by rememberSaveable {
        mutableStateOf((selected as? AgendaRange.Custom)?.days?.toString() ?: "")
    }

    FullScreenPicker(title = title, onDismiss = onDismiss) {
        PickerDescription(description)
        presets.forEachIndexed { index, option ->
            val isSelected = option == selected
            GroupedRow(
                title = agendaRangeLabel(option),
                position = positionOf(index, rowCount),
                selected = isSelected,
                trailing = if (isSelected) {
                    { SelectedCheck() }
                } else {
                    null
                },
                onClick = {
                    onSelect(option)
                    onDismiss()
                },
            )
        }
        GroupedRow(
            title = if (customSelected) {
                agendaRangeLabel(selected)
            } else {
                stringResource(R.string.agenda_range_custom)
            },
            position = if (customExpanded) Position.Top else positionOf(presets.size, rowCount),
            selected = customSelected,
            trailing = if (customSelected) {
                { SelectedCheck() }
            } else {
                null
            },
            onClick = { customExpanded = !customExpanded },
        )
        AnimatedVisibility(
            visible = customExpanded,
            enter = calendarExpandEnter(),
            exit = calendarCollapseExit(),
        ) {
            CustomDaysEditor(
                amountText = amountText,
                onAmountChange = { amountText = it },
                onConfirm = { days ->
                    onSelect(AgendaRange.Custom(days))
                    onDismiss()
                },
            )
        }
    }
}

/** The expanded "Custom" day-count editor: an amount field (1–365) and Set. */
@Composable
private fun CustomDaysEditor(
    amountText: String,
    onAmountChange: (String) -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val days = amountText.toIntOrNull()
        ?.takeIf { it in AgendaRange.MIN_CUSTOM_DAYS..AgendaRange.MAX_CUSTOM_DAYS }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 22.dp, bottomEnd = 22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DialogAmountField(
                value = amountText,
                onValueChange = onAmountChange,
                placeholder = "30",
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = days?.let { pluralStringResource(R.plurals.agenda_range_days, it, it) }
                    ?: stringResource(R.string.agenda_range_custom_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            FilledTonalButton(
                onClick = { days?.let(onConfirm) },
                enabled = days != null,
            ) {
                Text(stringResource(R.string.reminder_custom_set))
            }
        }
    }
}

/** Human label for an [AgendaRange] (used by the picker rows and settings summary). */
@Composable
fun agendaRangeLabel(range: AgendaRange): String = when (range) {
    AgendaRange.Day -> stringResource(R.string.agenda_range_day)
    AgendaRange.ThisWeek -> stringResource(R.string.agenda_range_this_week)
    AgendaRange.ThisMonth -> stringResource(R.string.agenda_range_this_month)
    AgendaRange.Week -> stringResource(R.string.agenda_range_week)
    AgendaRange.Month -> stringResource(R.string.agenda_range_month)
    is AgendaRange.Custom -> pluralStringResource(R.plurals.agenda_range_days, range.days, range.days)
}

@Composable
private fun reminderOverrideLabel(override: CalendarReminderOverride): String = when (override) {
    CalendarReminderOverride.Inherit -> stringResource(R.string.reminder_use_default)
    CalendarReminderOverride.None -> stringResource(R.string.reminder_none)
    is CalendarReminderOverride.Minutes -> reminderLeadTimeLabel(override.minutes)
}

/** Seed the custom editor: the largest exact unit for [minutes] (null → empty). */
private fun decomposeReminder(minutes: Int?): Pair<String, ReminderUnit> = when {
    minutes == null -> "" to ReminderUnit.Minutes
    minutes % 10_080 == 0 -> (minutes / 10_080).toString() to ReminderUnit.Weeks
    minutes % 1_440 == 0 -> (minutes / 1_440).toString() to ReminderUnit.Days
    minutes % 60 == 0 -> (minutes / 60).toString() to ReminderUnit.Hours
    else -> minutes.toString() to ReminderUnit.Minutes
}
