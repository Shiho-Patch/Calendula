package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
 * reuses the app's [CollapsingScaffold] (back button + full width), but with a
 * pinned single-line title rather than the large collapsing header — a picker is
 * a short list, so the tall header would only be empty space to scroll past.
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
        CollapsingScaffold(
            title = title,
            onBack = onDismiss,
            largeTopBar = false,
            content = content,
        )
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
    header: (@Composable ColumnScope.() -> Unit)? = null,
) {
    FullScreenPicker(title = title, onDismiss = onDismiss) {
        header?.invoke(this)
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
 * Reminder-default picker, full-screen and **multi-select**: each [presets]
 * lead time (plus any chosen custom value) is a checkbox row that toggles
 * independently, so a default can carry several reminders ("1 week before" *and*
 * "on the day"). Two exclusive checkmark rows sit above the list: an explicit
 * "No reminder" (both pickers, so the empty state stays visible and reachable)
 * and, for a per-calendar picker ([allowInherit]), "Use default reminder", which
 * defers to the global default. Clearing the last checked time reverts to "Use
 * default" on a per-calendar picker (so an accidental toggle-undo can't silently
 * wipe the calendar's default) and to explicit [CalendarReminderOverride.None]
 * on the global default (where empty legitimately means no reminder). A "Custom"
 * row expands an inline number field plus a unit selector to add an arbitrary
 * lead time to the set. Changes apply live via [onSelect]; the user leaves via
 * back.
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
    // Optimistic local state: once the user edits, the chosen override is
    // authoritative while the picker is open, so quick successive toggles compose
    // on each other instead of racing the round-trip through the settings flow
    // (which would drop a toggle made before the previous write echoes back).
    var current by remember { mutableStateOf(selected) }
    // Whether the user has toggled anything yet. Until then, [current] keeps
    // mirroring [selected]: a picker that first composed against the settings
    // flow's initialValue (empty — reachable after process-death restore straight
    // onto the Notifications page, before the CalendarProvider-gated combine
    // resolves) then adopts the real stored default instead of persisting empty
    // on the first tap. After the first edit, local state is authoritative.
    var userEdited by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        if (!userEdited) current = selected
    }
    val inherits = current is CalendarReminderOverride.Inherit
    val isNone = current is CalendarReminderOverride.None
    val selectedMinutes = (current as? CalendarReminderOverride.Minutes)?.minutes.orEmpty()
    // Custom (non-preset) lead times seen this session, so unchecking one keeps
    // its row (unchecked) until the picker closes instead of vanishing mid-tap,
    // which would strand a hand-entered value with no way to re-check it.
    val seenCustom = remember { mutableSetOf<Int>() }
    seenCustom += selectedMinutes.filter { it !in presets }
    // Presets plus every custom value seen this session, each as its own row.
    val rows = presets + seenCustom.sorted()

    var customExpanded by rememberSaveable { mutableStateOf(false) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(ReminderUnit.Minutes) }

    fun apply(override: CalendarReminderOverride) {
        userEdited = true
        current = override
        onSelect(override)
    }
    fun emit(minutes: List<Int>) = apply(reminderOverrideForMinutes(minutes, allowInherit))
    fun toggle(minute: Int) =
        emit(if (minute in selectedMinutes) selectedMinutes - minute else selectedMinutes + minute)

    FullScreenPicker(title = title, onDismiss = onDismiss) {
        // Exclusive choices in their own group above the multi-select list, since
        // each is mutually exclusive with picking specific times: "use default"
        // (per-calendar only) and an explicit "no reminder" (both pickers, so the
        // empty state stays visible and deliberately reachable).
        if (allowInherit) {
            GroupedRow(
                title = stringResource(R.string.reminder_use_default),
                position = Position.Top,
                selected = inherits,
                trailing = if (inherits) {
                    { SelectedCheck() }
                } else {
                    null
                },
                onClick = { apply(CalendarReminderOverride.Inherit) },
            )
        }
        GroupedRow(
            title = stringResource(R.string.reminder_none),
            position = if (allowInherit) Position.Bottom else Position.Alone,
            selected = isNone,
            trailing = if (isNone) {
                { SelectedCheck() }
            } else {
                null
            },
            onClick = { apply(CalendarReminderOverride.None) },
        )
        Spacer(Modifier.height(24.dp))
        val rowCount = rows.size + 1 // + the custom row
        rows.forEachIndexed { index, minute ->
            val checked = minute in selectedMinutes
            GroupedRow(
                title = reminderLeadTimeLabel(minute),
                position = positionOf(index, rowCount),
                selected = checked,
                trailing = { Checkbox(checked = checked, onCheckedChange = { toggle(minute) }) },
                onClick = { toggle(minute) },
            )
        }
        // When expanded, the Custom row connects downward into the editor card
        // so the two read as one grouped container (the per-calendar pattern).
        GroupedRow(
            title = stringResource(R.string.event_edit_reminder_custom),
            position = if (customExpanded) Position.Top else positionOf(rows.size, rowCount),
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
                    emit(selectedMinutes + minutes)
                    amountText = ""
                    customExpanded = false
                },
            )
        }
    }
}

/**
 * The override a lead-time set maps to when emitted from [ReminderDefaultPicker].
 * A non-empty set is [CalendarReminderOverride.Minutes]; an empty set (the last
 * time was unchecked) reverts to [CalendarReminderOverride.Inherit] on a
 * per-calendar picker ([allowInherit]) so an accidental toggle-undo can't
 * silently wipe the calendar's default, and to explicit
 * [CalendarReminderOverride.None] on the global default (where empty legitimately
 * means no reminder). Pure so it can be unit-tested.
 */
internal fun reminderOverrideForMinutes(
    minutes: List<Int>,
    allowInherit: Boolean,
): CalendarReminderOverride {
    val norm = minutes.distinct().sorted()
    return when {
        norm.isNotEmpty() -> CalendarReminderOverride.Minutes(norm)
        allowInherit -> CalendarReminderOverride.Inherit
        else -> CalendarReminderOverride.None
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
 * Agenda-range picker, full-screen. Two grouped lists — the calendar-aligned
 * options (today / this week / this month) and the rolling windows (next 7 / 30
 * days), with a "Custom" row that expands an inline day-count editor (1–365).
 * Mirrors [ReminderDefaultPicker]'s custom-expand pattern.
 */
@Composable
fun AgendaRangePicker(
    title: String,
    description: String,
    selected: AgendaRange,
    onSelect: (AgendaRange) -> Unit,
    onDismiss: () -> Unit,
) {
    val calendarAligned = listOf(AgendaRange.Day, AgendaRange.ThisWeek, AgendaRange.ThisMonth)
    val rolling = listOf(AgendaRange.Week, AgendaRange.Month)
    val rollingRowCount = rolling.size + 1 // + the custom row
    val customSelected = selected is AgendaRange.Custom

    var customExpanded by rememberSaveable { mutableStateOf(false) }
    var amountText by rememberSaveable {
        mutableStateOf((selected as? AgendaRange.Custom)?.days?.toString() ?: "")
    }

    val rangeRow: @Composable (AgendaRange, Position) -> Unit = { option, position ->
        val isSelected = option == selected
        GroupedRow(
            title = agendaRangeLabel(option),
            position = position,
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

    FullScreenPicker(title = title, onDismiss = onDismiss) {
        PickerDescription(description)

        // Calendar-aligned windows.
        calendarAligned.forEachIndexed { index, option ->
            rangeRow(option, positionOf(index, calendarAligned.size))
        }

        Spacer(Modifier.height(12.dp))

        // Rolling windows + custom.
        rolling.forEachIndexed { index, option ->
            rangeRow(option, positionOf(index, rollingRowCount))
        }
        GroupedRow(
            title = if (customSelected) {
                agendaRangeLabel(selected)
            } else {
                stringResource(R.string.agenda_range_custom)
            },
            position = if (customExpanded) Position.Top else positionOf(rolling.size, rollingRowCount),
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

