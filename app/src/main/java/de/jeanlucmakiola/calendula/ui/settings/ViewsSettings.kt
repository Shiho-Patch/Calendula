package de.jeanlucmakiola.calendula.ui.settings

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
import de.jeanlucmakiola.calendula.data.prefs.TimeFormatPref
import de.jeanlucmakiola.calendula.data.prefs.WeekStartPref
import de.jeanlucmakiola.calendula.data.prefs.resolveFirstDay
import de.jeanlucmakiola.calendula.ui.common.AgendaRangePicker
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.IMPLEMENTED_VIEWS
import de.jeanlucmakiola.calendula.ui.common.PickerDescription
import de.jeanlucmakiola.calendula.ui.common.QuickSwitchConfig
import de.jeanlucmakiola.calendula.ui.common.TimelineScale
import de.jeanlucmakiola.calendula.ui.common.agendaRangeLabel
import de.jeanlucmakiola.calendula.ui.common.descriptionRes
import de.jeanlucmakiola.calendula.ui.common.formatTimeOfDay
import de.jeanlucmakiola.calendula.ui.common.icon
import de.jeanlucmakiola.calendula.ui.common.labelRes
import de.jeanlucmakiola.calendula.ui.month.labelRes
import de.jeanlucmakiola.floret.components.CollapsingScaffold
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.OptionPicker
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.floret.components.ReorderableColumn
import de.jeanlucmakiola.floret.components.ReorderableRowHeight
import de.jeanlucmakiola.floret.locale.currentLocale
import kotlinx.datetime.DayOfWeek
import java.time.format.TextStyle as JavaTextStyle

/**
 * Views: everything that changes how a calendar view reads, grouped by the view
 * it belongs to, plus the two cross-view ordering lists (#24, #69).
 *
 * The quick-switch cycle and the drawer list are independent orders — a view
 * off in the cycle is still reachable from the drawer. The cycle needs at least
 * [QuickSwitchConfig.MIN_ENABLED] targets.
 */
@Composable
internal fun ViewsScreen(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    var showMonthStyle by remember { mutableStateOf(false) }
    var showDefaultView by remember { mutableStateOf(false) }
    var showWeekStart by remember { mutableStateOf(false) }
    var showTimeFormat by remember { mutableStateOf(false) }
    var showPastEvents by remember { mutableStateOf(false) }
    var showAgendaScreenRange by remember { mutableStateOf(false) }
    var showTimelineScale by remember { mutableStateOf(false) }

    CollapsingScaffold(
        title = stringResource(R.string.settings_section_views),
        onBack = onBack,
        predictiveBack = true,
    ) {
        val config = state.quickSwitchConfig

        // What holds for every view, above the per-view groups.
        SectionHeader(stringResource(R.string.settings_views_all_header))
        GroupedRow(
            title = stringResource(R.string.settings_default_view),
            summary = stringResource(state.defaultView.labelRes),
            position = Position.Top,
            onClick = { showDefaultView = true },
        )
        GroupedRow(
            title = stringResource(R.string.settings_week_start),
            summary = weekStartLabel(state.weekStart),
            position = Position.Middle,
            onClick = { showWeekStart = true },
        )
        GroupedRow(
            title = stringResource(R.string.settings_time_format),
            summary = timeFormatLabel(state.timeFormat),
            position = Position.Middle,
            onClick = { showTimeFormat = true },
        )
        GroupedRow(
            title = stringResource(R.string.settings_today_toolbar),
            summary = stringResource(R.string.settings_today_toolbar_summary),
            position = Position.Middle,
            trailing = {
                Switch(
                    checked = state.todayButtonInToolbar,
                    onCheckedChange = viewModel::setTodayButtonInToolbar,
                )
            },
            onClick = { viewModel.setTodayButtonInToolbar(!state.todayButtonInToolbar) },
        )
        GroupedRow(
            title = stringResource(R.string.settings_dim_completed),
            summary = stringResource(R.string.settings_dim_completed_summary),
            position = Position.Middle,
            trailing = {
                Switch(
                    checked = state.dimCompletedEvents,
                    onCheckedChange = viewModel::setDimCompletedEvents,
                )
            },
            onClick = { viewModel.setDimCompletedEvents(!state.dimCompletedEvents) },
        )
        GroupedRow(
            title = stringResource(R.string.settings_drag_to_reschedule),
            summary = stringResource(R.string.settings_drag_to_reschedule_summary),
            position = Position.Bottom,
            trailing = {
                Switch(
                    checked = state.dragToReschedule,
                    onCheckedChange = viewModel::setDragToReschedule,
                )
            },
            onClick = { viewModel.setDragToReschedule(!state.dragToReschedule) },
        )

        Spacer(Modifier.height(8.dp))
        SectionHeader(stringResource(R.string.settings_month_header))
        GroupedRow(
            title = stringResource(R.string.settings_month_view_style),
            summary = stringResource(state.monthViewStyle.labelRes),
            position = Position.Top,
            onClick = { showMonthStyle = true },
        )
        GroupedRow(
            title = stringResource(R.string.settings_week_numbers),
            summary = stringResource(R.string.settings_week_numbers_summary),
            position = Position.Bottom,
            trailing = {
                Switch(
                    checked = state.showWeekNumbers,
                    onCheckedChange = viewModel::setShowWeekNumbers,
                )
            },
            onClick = { viewModel.setShowWeekNumbers(!state.showWeekNumbers) },
        )

        Spacer(Modifier.height(8.dp))
        SectionHeader(stringResource(R.string.settings_week_day_header))
        GroupedRow(
            title = stringResource(R.string.settings_timeline_scale),
            summary = stringResource(state.timelineScale.labelRes),
            position = Position.Top,
            onClick = { showTimelineScale = true },
        )
        GroupedRow(
            title = stringResource(R.string.settings_hour_lines),
            summary = stringResource(R.string.settings_hour_lines_summary),
            position = Position.Bottom,
            trailing = {
                Switch(
                    checked = state.showHourLines,
                    onCheckedChange = viewModel::setShowHourLines,
                )
            },
            onClick = { viewModel.setShowHourLines(!state.showHourLines) },
        )

        Spacer(Modifier.height(8.dp))
        SectionHeader(stringResource(R.string.settings_agenda_header))
        GroupedRow(
            title = stringResource(R.string.settings_agenda_range),
            summary = agendaRangeLabel(state.agendaScreenRange),
            position = Position.Top,
            onClick = { showAgendaScreenRange = true },
        )
        GroupedRow(
            title = stringResource(R.string.settings_past_events),
            summary = pastEventDisplayLabel(state.pastEventDisplay),
            position = Position.Middle,
            onClick = { showPastEvents = true },
        )
        GroupedRow(
            title = stringResource(R.string.settings_agenda_show_today),
            summary = stringResource(R.string.settings_agenda_show_today_hint),
            position = Position.Middle,
            trailing = {
                Switch(
                    checked = state.agendaShowToday,
                    onCheckedChange = viewModel::setAgendaShowToday,
                )
            },
            onClick = { viewModel.setAgendaShowToday(!state.agendaShowToday) },
        )
        GroupedRow(
            title = stringResource(R.string.settings_agenda_range_bar),
            summary = stringResource(R.string.settings_agenda_range_bar_hint),
            position = Position.Bottom,
            trailing = {
                Switch(
                    checked = state.agendaShowRangeBar,
                    onCheckedChange = viewModel::setAgendaShowRangeBar,
                )
            },
            onClick = { viewModel.setAgendaShowRangeBar(!state.agendaShowRangeBar) },
        )

        Spacer(Modifier.height(24.dp))
        SectionHeader(stringResource(R.string.settings_quick_switch_header))
        SettingsHint(stringResource(R.string.settings_quick_switch_hint))
        Spacer(Modifier.height(8.dp))
        // Turning a view off is blocked once only the minimum remain enabled.
        val canDisable = config.enabled.size > QuickSwitchConfig.MIN_ENABLED
        ReorderableColumn(
            items = config.order,
            keyOf = { it },
            onReorder = { viewModel.setQuickSwitchOrder(it) },
        ) { view, position, dragHandle, isDragging ->
            val checked = view in config.enabled
            ViewRow(
                view = view,
                position = position,
                isDragging = isDragging,
                dragHandle = dragHandle,
                dimmed = !checked,
                trailing = {
                    Switch(
                        checked = checked,
                        // Keep the last two on: with fewer, the pill can't switch.
                        enabled = !checked || canDisable,
                        onCheckedChange = { on -> viewModel.setQuickSwitchViewEnabled(view, on) },
                    )
                },
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionHeader(stringResource(R.string.settings_drawer_order_header))
        SettingsHint(stringResource(R.string.settings_drawer_order_hint))
        Spacer(Modifier.height(8.dp))
        ReorderableColumn(
            items = state.drawerViewOrder,
            keyOf = { it },
            onReorder = { viewModel.setDrawerViewOrder(it) },
        ) { view, position, dragHandle, isDragging ->
            ViewRow(
                view = view,
                position = position,
                isDragging = isDragging,
                dragHandle = dragHandle,
            )
        }
    }

    if (showMonthStyle) {
        MonthViewStylePicker(
            selected = state.monthViewStyle,
            // The preview is a real grid, so it uses the real week start too.
            weekStart = state.weekStart.resolveFirstDay(currentLocale()),
            onSelect = viewModel::setMonthViewStyle,
            onDismiss = { showMonthStyle = false },
        )
    }
    if (showDefaultView) {
        OptionPicker(
            title = stringResource(R.string.settings_default_view),
            header = { PickerDescription(stringResource(R.string.settings_default_view_hint)) },
            predictiveBack = true,
            options = IMPLEMENTED_VIEWS,
            selected = state.defaultView,
            label = { stringResource(it.labelRes) },
            leading = {
                Icon(
                    imageVector = it.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onSelect = viewModel::setDefaultView,
            onDismiss = { showDefaultView = false },
        )
    }
    if (showWeekStart) {
        WeekStartPicker(
            selected = state.weekStart,
            // The preview is a real grid, so it uses the user's own month style.
            monthStyle = state.monthViewStyle,
            options = WEEK_START_OPTIONS,
            onSelect = viewModel::setWeekStart,
            onDismiss = { showWeekStart = false },
        )
    }
    if (showTimeFormat) {
        val locale = currentLocale()
        val systemIs24Hour = DateFormat.is24HourFormat(LocalContext.current)
        OptionPicker(
            title = stringResource(R.string.settings_time_format),
            header = { PickerDescription(stringResource(R.string.settings_time_format_hint)) },
            predictiveBack = true,
            options = TimeFormatPref.entries,
            selected = state.timeFormat,
            label = { timeFormatLabel(it) },
            // Each option renders the same sample time its own way;
            // "Automatic" also names which of the two it resolves to.
            summary = { pref ->
                val sample = formatTimeOfDay(
                    hour = SAMPLE_HOUR,
                    minute = SAMPLE_MINUTE,
                    is24Hour = when (pref) {
                        TimeFormatPref.AUTO -> systemIs24Hour
                        TimeFormatPref.TWELVE_HOUR -> false
                        TimeFormatPref.TWENTY_FOUR_HOUR -> true
                    },
                    locale = locale,
                )
                if (pref == TimeFormatPref.AUTO) {
                    stringResource(R.string.settings_time_format_auto_summary, sample)
                } else {
                    sample
                }
            },
            onSelect = viewModel::setTimeFormat,
            onDismiss = { showTimeFormat = false },
        )
    }
    if (showPastEvents) {
        PastEventsPicker(
            selected = state.pastEventDisplay,
            onSelect = viewModel::setPastEventDisplay,
            onDismiss = { showPastEvents = false },
        )
    }
    if (showTimelineScale) {
        OptionPicker(
            title = stringResource(R.string.settings_timeline_scale),
            header = { PickerDescription(stringResource(R.string.settings_timeline_scale_hint)) },
            predictiveBack = true,
            // A height pinched on the timeline is listed alongside the presets
            // rather than left as a silently unticked list: it is the current
            // setting, so it has to be visible here, and seeing it next to the
            // named steps is what makes "tap one to go back" obvious.
            options = TimelineScale.presets + listOfNotNull(
                state.timelineScale as? TimelineScale.Custom,
            ),
            selected = state.timelineScale,
            label = { stringResource(it.labelRes) },
            summary = { stringResource(it.descriptionRes) },
            onSelect = viewModel::setTimelineScale,
            onDismiss = { showTimelineScale = false },
        )
    }
    if (showAgendaScreenRange) {
        AgendaRangePicker(
            title = stringResource(R.string.settings_agenda_range),
            description = stringResource(R.string.settings_agenda_range_hint),
            selected = state.agendaScreenRange,
            weekStart = state.weekStart.resolveFirstDay(currentLocale()),
            onSelect = viewModel::setAgendaScreenRange,
            onDismiss = { showAgendaScreenRange = false },
        )
    }
}

/** Sample time the format options render: afternoon, where 12h and 24h differ. */
private const val SAMPLE_HOUR = 14
private const val SAMPLE_MINUTE = 30

/** One reorderable view row: the view's icon and name, an optional [trailing]
 *  control, and a drag handle carrying the [dragHandle] gesture modifier. */
@Composable
private fun ViewRow(
    view: CalendarView,
    position: Position,
    isDragging: Boolean,
    dragHandle: Modifier,
    dimmed: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    GroupedRow(
        title = stringResource(view.labelRes),
        position = position,
        dimmed = dimmed,
        minHeight = ReorderableRowHeight,
        // The reorderable column owns the inter-row spacing (uniform pitch).
        gapBelow = false,
        container = if (isDragging) MaterialTheme.colorScheme.secondaryContainer else null,
        leading = {
            Icon(
                imageVector = view.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailing?.invoke()
                if (trailing != null) Spacer(Modifier.width(8.dp))
                Box(
                    modifier = dragHandle.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = stringResource(R.string.reorder_drag_handle),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

/** Picker options: "Follow system" first, then Monday…Sunday in ISO order. */
private val WEEK_START_OPTIONS: List<WeekStartPref> =
    listOf(WeekStartPref.Auto) + DayOfWeek.entries.map { WeekStartPref.Day(it) }

@Composable
internal fun weekStartLabel(pref: WeekStartPref): String = when (pref) {
    WeekStartPref.Auto -> stringResource(R.string.settings_week_start_auto)
    // Localised weekday name, so no per-day string resource is needed.
    is WeekStartPref.Day -> java.time.DayOfWeek.of(pref.day.ordinal + 1)
        .getDisplayName(JavaTextStyle.FULL, currentLocale())
}

@Composable
private fun timeFormatLabel(pref: TimeFormatPref): String = stringResource(
    when (pref) {
        TimeFormatPref.AUTO -> R.string.settings_time_format_auto
        TimeFormatPref.TWELVE_HOUR -> R.string.settings_time_format_12h
        TimeFormatPref.TWENTY_FOUR_HOUR -> R.string.settings_time_format_24h
    },
)

@Composable
private fun pastEventDisplayLabel(mode: PastEventDisplay): String =
    stringResource(pastEventDisplayLabelRes(mode))

/** Shared with [PastEventsPicker], which needs the id rather than the string. */
@StringRes
internal fun pastEventDisplayLabelRes(mode: PastEventDisplay): Int = when (mode) {
    PastEventDisplay.SHOW -> R.string.settings_past_events_show
    PastEventDisplay.DIM -> R.string.settings_past_events_dim
    PastEventDisplay.HIDE -> R.string.settings_past_events_hide
}
