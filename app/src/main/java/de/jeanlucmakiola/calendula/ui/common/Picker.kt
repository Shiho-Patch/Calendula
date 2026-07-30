package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.floret.components.CustomAmountEditor
import de.jeanlucmakiola.floret.components.FullScreenPicker
import de.jeanlucmakiola.floret.components.GroupedListInset
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.floret.components.SelectedCheck
import de.jeanlucmakiola.floret.components.positionOf
import de.jeanlucmakiola.floret.identity.collapseExit
import de.jeanlucmakiola.floret.identity.expandEnter
import de.jeanlucmakiola.floret.locale.currentLocale
import de.jeanlucmakiola.floret.reminders.ReminderOverride
import de.jeanlucmakiola.floret.reminders.ReminderUnit
import de.jeanlucmakiola.floret.reminders.reminderOverrideForMinutes
import de.jeanlucmakiola.calendula.ui.agenda.AgendaRange
import de.jeanlucmakiola.calendula.ui.agenda.agendaRangeWindowSummary
import de.jeanlucmakiola.calendula.ui.agenda.dayCount
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Reminder-default picker, full-screen and **multi-select**: each [presets]
 * lead time (plus any chosen custom value) is a checkbox row that toggles
 * independently, so a default can carry several reminders ("1 week before" *and*
 * "on the day"). Two exclusive checkmark rows sit above the list: an explicit
 * "No reminder" (both pickers, so the empty state stays visible and reachable)
 * and, for a per-calendar picker ([allowInherit]), "Use default reminder", which
 * defers to the global default. Clearing the last checked time reverts to "Use
 * default" on a per-calendar picker (so an accidental toggle-undo can't silently
 * wipe the calendar's default) and to explicit [ReminderOverride.None]
 * on the global default (where empty legitimately means no reminder). A "Custom"
 * row expands an inline number field plus a unit selector to add an arbitrary
 * lead time to the set. Changes apply live via [onSelect]; the user leaves via
 * back.
 *
 * [leadTimeSummary] adds a second line to each lead-time row, for the all-day
 * pickers: the hour comes from the separate "show all-day reminders at" setting,
 * so the lead time alone doesn't say when anything happens.
 */
@Composable
fun ReminderDefaultPicker(
    title: String,
    presets: List<Int>,
    selected: ReminderOverride,
    allowInherit: Boolean,
    onSelect: (ReminderOverride) -> Unit,
    onDismiss: () -> Unit,
    leadTimeSummary: (@Composable (Int) -> String?)? = null,
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
    val inherits = current is ReminderOverride.Inherit
    val isNone = current is ReminderOverride.None
    val selectedMinutes = (current as? ReminderOverride.Minutes)?.minutes.orEmpty()
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

    fun apply(override: ReminderOverride) {
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
                onClick = { apply(ReminderOverride.Inherit) },
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
            onClick = { apply(ReminderOverride.None) },
        )
        Spacer(Modifier.height(24.dp))
        val rowCount = rows.size + 1 // + the custom row
        rows.forEachIndexed { index, minute ->
            val checked = minute in selectedMinutes
            GroupedRow(
                title = reminderLeadTimeLabel(minute),
                summary = leadTimeSummary?.invoke(minute),
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
            enter = expandEnter(),
            exit = collapseExit(),
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
    CustomAmountEditor(
        amountText = amountText,
        onAmountChange = onAmountChange,
        unitLabels = ReminderUnit.entries.map { stringResource(reminderUnitLabel(it)) },
        selectedUnit = unit.ordinal,
        onUnitChange = { onUnitChange(ReminderUnit.entries[it]) },
        preview = amount?.let { reminderLeadTimeLabel(it * unit.minutesFactor) }
            ?: stringResource(R.string.reminder_custom_amount),
        setLabel = stringResource(R.string.reminder_custom_set),
        confirmEnabled = amount != null,
        onConfirm = { amount?.let { onConfirm(it * unit.minutesFactor) } },
    )
}

/** A short explanatory paragraph shown under a picker's title, above the rows,
 *  on the same left margin as the rows themselves. */
@Composable
internal fun PickerDescription(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = GroupedListInset, vertical = 8.dp),
    )
}


/**
 * Agenda-range picker, full-screen. Two grouped lists — the calendar-aligned
 * options (today / this week / this month) and the rolling windows (next 7 / 30
 * days), with a "Custom" row that expands an inline day-count editor (1–365).
 * Mirrors [ReminderDefaultPicker]'s custom-expand pattern.
 *
 * Every option carries the dates it resolves to today as its summary, computed
 * through the same [dayCount] the agenda windows by (hence [weekStart]) — only
 * the concrete span tells "This week" and "Next 7 days" apart.
 */
@Composable
fun AgendaRangePicker(
    title: String,
    description: String,
    selected: AgendaRange,
    weekStart: DayOfWeek,
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

    val locale = currentLocale()
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = remember(zone) { Clock.System.now().toLocalDateTime(zone).date }
    // The agenda's own end-day arithmetic (anchor + dayCount - 1), so the dates
    // match what it will show.
    val windowSummary: (AgendaRange) -> String = { range ->
        val end = today.plus(range.dayCount(today, weekStart) - 1, DateTimeUnit.DAY)
        agendaRangeWindowSummary(range, today, end, locale, monthAsSpan = true)
    }

    val rangeRow: @Composable (AgendaRange, Position) -> Unit = { option, position ->
        val isSelected = option == selected
        GroupedRow(
            title = agendaRangeLabel(option),
            summary = windowSummary(option),
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
            // An unset custom window has no day count yet, so the row stays
            // single-line until it does.
            summary = if (customSelected) windowSummary(selected) else null,
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
            enter = expandEnter(),
            exit = collapseExit(),
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
    CustomAmountEditor(
        amountText = amountText,
        onAmountChange = onAmountChange,
        placeholder = "30",
        preview = days?.let { pluralStringResource(R.plurals.agenda_range_days, it, it) }
            ?: stringResource(R.string.agenda_range_custom_hint),
        setLabel = stringResource(R.string.reminder_custom_set),
        confirmEnabled = days != null,
        onConfirm = { days?.let(onConfirm) },
    )
}

/**
 * Snooze-duration picker, full-screen and **single-select**: the [presets]
 * (whole-minute delays) each sit as a checkmark row, with a "Custom" row that
 * expands an inline amount field plus a Minutes/Hours unit toggle to enter an
 * arbitrary delay. Mirrors [AgendaRangePicker]'s custom-expand pattern; picking
 * a preset or confirming a custom value applies via [onSelect] and closes.
 * [label] renders a delay in minutes as a duration ("10 minutes", "1 hour") and
 * is reused for both the rows and the custom preview.
 */
@Composable
fun SnoozeDurationPicker(
    title: String,
    presets: List<Int>,
    selected: Int,
    label: @Composable (Int) -> String,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val customSelected = selected !in presets
    val rowCount = presets.size + 1 // + the custom row

    var customExpanded by rememberSaveable { mutableStateOf(false) }
    var amountText by rememberSaveable {
        mutableStateOf(if (customSelected) snoozeCustomAmount(selected).toString() else "")
    }
    var unit by rememberSaveable {
        mutableStateOf(if (customSelected) snoozeCustomUnit(selected) else ReminderUnit.Minutes)
    }

    FullScreenPicker(title = title, onDismiss = onDismiss, predictiveBack = true) {
        presets.forEachIndexed { index, minute ->
            val isSelected = minute == selected
            GroupedRow(
                title = label(minute),
                position = positionOf(index, rowCount),
                selected = isSelected,
                trailing = if (isSelected) {
                    { SelectedCheck() }
                } else {
                    null
                },
                onClick = {
                    onSelect(minute)
                    onDismiss()
                },
            )
        }
        // The Custom row connects downward into the editor card when expanded, so
        // the two read as one grouped container (the shared custom-expand pattern).
        GroupedRow(
            title = if (customSelected) label(selected) else stringResource(R.string.event_edit_reminder_custom),
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
            enter = expandEnter(),
            exit = collapseExit(),
        ) {
            CustomSnoozeEditor(
                amountText = amountText,
                onAmountChange = { amountText = it },
                unit = unit,
                onUnitChange = { unit = it },
                label = label,
                onConfirm = { minutes ->
                    onSelect(minutes)
                    onDismiss()
                },
            )
        }
    }
}

/** Whole hours if the delay divides evenly, else minutes. */
private fun snoozeCustomUnit(minutes: Int): ReminderUnit =
    if (minutes % ReminderUnit.Hours.minutesFactor == 0) ReminderUnit.Hours else ReminderUnit.Minutes

private fun snoozeCustomAmount(minutes: Int): Int =
    if (minutes % ReminderUnit.Hours.minutesFactor == 0) minutes / ReminderUnit.Hours.minutesFactor else minutes

/**
 * The expanded "Custom" snooze editor: a tonal card connected to the Custom row
 * above it. A Minutes/Hours unit toggle, an amount field with a live preview of
 * the delay it resolves to, and a tonal confirm enabled only for a valid 1–999
 * amount. [onConfirm] receives the final delay in minutes.
 */
@Composable
private fun CustomSnoozeEditor(
    amountText: String,
    onAmountChange: (String) -> Unit,
    unit: ReminderUnit,
    onUnitChange: (ReminderUnit) -> Unit,
    label: @Composable (Int) -> String,
    onConfirm: (Int) -> Unit,
) {
    val units = remember { listOf(ReminderUnit.Minutes, ReminderUnit.Hours) }
    val amount = amountText.toIntOrNull()?.takeIf { it in 1..999 }
    CustomAmountEditor(
        amountText = amountText,
        onAmountChange = onAmountChange,
        unitLabels = units.map { stringResource(reminderUnitLabel(it)) },
        selectedUnit = units.indexOf(unit).coerceAtLeast(0),
        onUnitChange = { onUnitChange(units[it]) },
        preview = amount?.let { label(it * unit.minutesFactor) }
            ?: stringResource(R.string.reminder_custom_amount),
        setLabel = stringResource(R.string.reminder_custom_set),
        confirmEnabled = amount != null,
        onConfirm = { amount?.let { onConfirm(it * unit.minutesFactor) } },
    )
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

