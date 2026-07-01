package de.jeanlucmakiola.calendula.ui.settings

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.crash.CrashReporter
import de.jeanlucmakiola.calendula.data.prefs.CalendarReminderOverride
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
import de.jeanlucmakiola.calendula.data.prefs.ThemeMode
import de.jeanlucmakiola.calendula.data.prefs.TimeFormatPref
import de.jeanlucmakiola.calendula.data.prefs.WeekStartPref
import de.jeanlucmakiola.calendula.domain.EventFormField
import de.jeanlucmakiola.calendula.domain.contacts.SpecialDateType
import de.jeanlucmakiola.calendula.qs.NewEventTileService
import de.jeanlucmakiola.calendula.ui.crash.CrashReportDialog
import de.jeanlucmakiola.calendula.ui.crash.openIssueTracker
import de.jeanlucmakiola.calendula.ui.crash.submitCrashReport
import de.jeanlucmakiola.calendula.ui.common.calendarCollapseExit
import de.jeanlucmakiola.calendula.ui.common.calendarExpandEnter
import de.jeanlucmakiola.calendula.ui.common.AgendaRangePicker
import de.jeanlucmakiola.calendula.ui.common.agendaRangeLabel
import de.jeanlucmakiola.calendula.ui.common.CollapsingScaffold
import de.jeanlucmakiola.calendula.ui.common.GroupedRow
import de.jeanlucmakiola.calendula.ui.common.IMPLEMENTED_VIEWS
import de.jeanlucmakiola.calendula.ui.common.labelRes
import de.jeanlucmakiola.calendula.ui.common.CalendarColorChip
import de.jeanlucmakiola.calendula.ui.common.OptionPicker
import de.jeanlucmakiola.calendula.ui.common.Position
import de.jeanlucmakiola.calendula.ui.common.REMINDER_PRESETS
import de.jeanlucmakiola.calendula.ui.common.ReminderDefaultPicker
import de.jeanlucmakiola.calendula.ui.common.TimePickerAlert
import de.jeanlucmakiola.calendula.ui.common.positionOf
import de.jeanlucmakiola.calendula.ui.common.reminderLeadTimeLabel
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarSlideSpec
import de.jeanlucmakiola.calendula.ui.common.currentLocale
import de.jeanlucmakiola.calendula.ui.common.eventFormFieldIcon
import de.jeanlucmakiola.calendula.ui.common.eventFormFieldLabel
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import java.time.format.TextStyle as JavaTextStyle
import java.util.Calendar

/** The settings sub-screens reached from the hub's category rows. */
private enum class SettingsSection { Appearance, EventForm, Notifications, SpecialDates }

/**
 * Token-based accent for a leading icon chip (container / on-container pair).
 * Neutral chips stay grey; accents are drawn from the M3 scheme so they adapt
 * to theme, dark mode and dynamic colour.
 */
private enum class ChipAccent { Neutral, Primary, Tertiary }

/**
 * Settings (M4), restructured in v2.3 into a category hub with sub-screens.
 * Both the hub and the sub-screens use a collapsing [LargeTopAppBar] and the
 * grouped-row card system. Calendars opens the separate manager hoisted in
 * [CalendarHost]; Language opens an inline OptionCard dialog; About is a card
 * at the top. A full-screen destination; [onBack] pops it.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onManageCalendars: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var section by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    val slideSpec = rememberCalendarSlideSpec()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        SettingsHub(
            onBack = onBack,
            onOpenSection = { section = it },
            onManageCalendars = onManageCalendars,
        )

        AnimatedVisibility(
            visible = section == SettingsSection.Appearance,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(),
            exit = slideOutHorizontally(slideSpec) { it } + fadeOut(),
        ) {
            AppearanceScreen(state = state, viewModel = viewModel, onBack = { section = null })
        }
        AnimatedVisibility(
            visible = section == SettingsSection.EventForm,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(),
            exit = slideOutHorizontally(slideSpec) { it } + fadeOut(),
        ) {
            EventFormScreen(state = state, viewModel = viewModel, onBack = { section = null })
        }
        AnimatedVisibility(
            visible = section == SettingsSection.Notifications,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(),
            exit = slideOutHorizontally(slideSpec) { it } + fadeOut(),
        ) {
            NotificationsScreen(state = state, viewModel = viewModel, onBack = { section = null })
        }
        AnimatedVisibility(
            visible = section == SettingsSection.SpecialDates,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(),
            exit = slideOutHorizontally(slideSpec) { it } + fadeOut(),
        ) {
            SpecialDatesScreen(viewModel = viewModel, onBack = { section = null })
        }
    }
}

// ---------------------------------------------------------------------------
// Hub
// ---------------------------------------------------------------------------

@Composable
private fun SettingsHub(
    onBack: () -> Unit,
    onOpenSection: (SettingsSection) -> Unit,
    onManageCalendars: () -> Unit,
) {
    CollapsingScaffold(title = stringResource(R.string.settings_title), onBack = onBack) {
        Box(Modifier.padding(horizontal = 16.dp)) { AboutCard() }
        Spacer(Modifier.height(16.dp))

        GroupedRow(
            title = stringResource(R.string.settings_section_appearance),
            summary = stringResource(R.string.settings_appearance_subtitle),
            position = Position.Top,
            leading = { CategoryIcon(Icons.Default.Palette, ChipAccent.Neutral) },
            onClick = { onOpenSection(SettingsSection.Appearance) },
        )
        GroupedRow(
            title = stringResource(R.string.settings_section_event_form),
            summary = stringResource(R.string.settings_event_form_subtitle),
            position = Position.Middle,
            leading = { CategoryIcon(Icons.Default.Tune, ChipAccent.Neutral) },
            onClick = { onOpenSection(SettingsSection.EventForm) },
        )
        GroupedRow(
            title = stringResource(R.string.settings_section_notifications),
            summary = stringResource(R.string.settings_notifications_subtitle),
            position = Position.Middle,
            leading = { CategoryIcon(Icons.Default.Notifications, ChipAccent.Primary) },
            onClick = { onOpenSection(SettingsSection.Notifications) },
        )
        GroupedRow(
            title = stringResource(R.string.settings_section_calendars),
            summary = stringResource(R.string.settings_manage_calendars_hint),
            position = Position.Middle,
            leading = { CategoryIcon(Icons.Default.CalendarMonth, ChipAccent.Tertiary) },
            onClick = onManageCalendars,
        )
        GroupedRow(
            title = stringResource(R.string.settings_section_special_dates),
            summary = stringResource(R.string.settings_special_dates_subtitle),
            position = Position.Middle,
            leading = { CategoryIcon(Icons.Default.Cake, ChipAccent.Tertiary) },
            onClick = { onOpenSection(SettingsSection.SpecialDates) },
        )
        LanguageRow(position = Position.Middle)
        // One-tap add of the "New event" Quick Settings tile. The system prompt
        // is API 33+; on older versions the tile is still addable manually from
        // the QS editor, so the row simply doesn't appear here.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            QuickSettingsTileRow(position = Position.Middle)
        }
        ReportProblemRow(position = Position.Bottom)

        AppVersionText()
    }
}

/**
 * Opens the project's issue tracker to report a problem. If a crash report was
 * captured (and not yet sent), it surfaces that report first via the same
 * dialog the next-launch prompt uses; otherwise it opens the issue template
 * chooser. No data leaves the device until the user submits the issue.
 */
@Composable
private fun ReportProblemRow(position: Position) {
    val context = LocalContext.current
    var report by remember { mutableStateOf<String?>(null) }

    GroupedRow(
        title = stringResource(R.string.settings_report_problem),
        summary = stringResource(R.string.settings_report_problem_hint),
        position = position,
        leading = { CategoryIcon(Icons.Default.BugReport, ChipAccent.Neutral) },
        onClick = {
            val pending = CrashReporter.pendingReport(context)
            if (pending != null) report = pending else openIssueTracker(context)
        },
    )

    report?.let { pending ->
        CrashReportDialog(
            report = pending,
            onSend = {
                submitCrashReport(context, pending)
                CrashReporter.clearReport(context)
                report = null
            },
            onDismiss = { report = null },
        )
    }
}

/**
 * Asks the system to add the "New event" Quick Settings tile (API 33+). A
 * top-level action row, like [ReportProblemRow] — the tile is a system-surface
 * shortcut, not part of any one settings category.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun QuickSettingsTileRow(position: Position) {
    val context = LocalContext.current
    GroupedRow(
        title = stringResource(R.string.settings_qs_tile),
        summary = stringResource(R.string.settings_qs_tile_hint),
        position = position,
        leading = { CategoryIcon(Icons.Default.Dashboard, ChipAccent.Neutral) },
        onClick = { requestAddQsTile(context) },
    )
}

@Composable
private fun LanguageRow(position: Position) {
    val context = LocalContext.current
    // Setting a locale recreates the activity; mirror the choice locally so the
    // row updates instantly even before the recreation lands.
    var current by remember { mutableStateOf(AppLanguage.currentTag()) }
    var showDialog by remember { mutableStateOf(false) }

    // null = follow the system; the rest are BCP-47 tags from locales_config.xml.
    val options = remember { listOf<String?>(null) + AppLanguage.supportedTags(context) }

    GroupedRow(
        title = stringResource(R.string.settings_language),
        summary = languageLabel(current),
        position = position,
        leading = { CategoryIcon(Icons.Default.Language, ChipAccent.Neutral) },
        onClick = { showDialog = true },
    )

    if (showDialog) {
        OptionPicker(
            title = stringResource(R.string.settings_language),
            options = options,
            selected = current,
            label = { languageLabel(it) },
            onSelect = {
                current = it
                AppLanguage.apply(it)
            },
            onDismiss = { showDialog = false },
            // Invite contributions right where users pick their language.
            header = {
                val translateUrl = stringResource(R.string.about_translate_url)
                GroupedRow(
                    title = stringResource(R.string.settings_translate),
                    summary = stringResource(R.string.settings_translate_hint),
                    position = Position.Alone,
                    leading = { CategoryIcon(Icons.Default.Translate, ChipAccent.Neutral) },
                    onClick = { openUrl(context, translateUrl) },
                )
                Spacer(Modifier.height(16.dp))
            },
        )
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val sourceUrl = stringResource(R.string.about_source_url)
    val licenseUrl = stringResource(R.string.about_license_url)
    val supportUrl = stringResource(R.string.about_support_url)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppLogo()
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.settings_about_author),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { openUrl(context, sourceUrl) },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_gitea),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_about_source))
                }
                OutlinedButton(
                    onClick = { openUrl(context, licenseUrl) },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Default.Gavel,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_license))
                }
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = { openUrl(context, supportUrl) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_about_support))
            }
        }
    }
}

/** Plain centred version mark at the foot of the settings list (no card). */
@Composable
private fun AppVersionText() {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }
    Text(
        text = stringResource(R.string.settings_about_version, versionName),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    )
}

/**
 * The app icon as a rounded chip: the off-white launcher mark over its slate
 * background colour, rendered oversized and clipped to fill the chip the way a
 * launcher mask would.
 */
@Composable
private fun AppLogo() {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(R.string.settings_about_logo_desc),
            modifier = Modifier.requiredSize(108.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Sub-screens
// ---------------------------------------------------------------------------

@Composable
private fun AppearanceScreen(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    var showTheme by remember { mutableStateOf(false) }
    var showWeekStart by remember { mutableStateOf(false) }
    var showTimeFormat by remember { mutableStateOf(false) }
    var showDefaultView by remember { mutableStateOf(false) }
    var showAgendaScreenRange by remember { mutableStateOf(false) }
    var showAgendaWidgetRange by remember { mutableStateOf(false) }
    var showPastEvents by remember { mutableStateOf(false) }

    CollapsingScaffold(
        title = stringResource(R.string.settings_section_appearance),
        onBack = onBack,
    ) {
        // Theme & colour
        GroupedRow(
            title = stringResource(R.string.settings_theme),
            summary = themeLabel(state.themeMode),
            position = Position.Top,
            onClick = { showTheme = true },
        )
        GroupedRow(
            title = stringResource(R.string.settings_dynamic_color),
            summary = if (state.dynamicColorAvailable) {
                null
            } else {
                stringResource(R.string.settings_dynamic_color_unavailable)
            },
            position = Position.Bottom,
            trailing = {
                Switch(
                    checked = state.dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                    enabled = state.dynamicColorAvailable,
                )
            },
            onClick = if (state.dynamicColorAvailable) {
                { viewModel.setDynamicColor(!state.dynamicColor) }
            } else {
                null
            },
        )

        Spacer(Modifier.height(16.dp))

        // Calendar — view, week, and timeline formatting
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
            title = stringResource(R.string.settings_hour_lines),
            summary = stringResource(R.string.settings_hour_lines_summary),
            position = Position.Middle,
            trailing = {
                Switch(
                    checked = state.showHourLines,
                    onCheckedChange = viewModel::setShowHourLines,
                )
            },
            onClick = { viewModel.setShowHourLines(!state.showHourLines) },
        )
        GroupedRow(
            title = stringResource(R.string.settings_dim_completed),
            summary = stringResource(R.string.settings_dim_completed_summary),
            position = Position.Bottom,
            trailing = {
                Switch(
                    checked = state.dimCompletedEvents,
                    onCheckedChange = viewModel::setDimCompletedEvents,
                )
            },
            onClick = { viewModel.setDimCompletedEvents(!state.dimCompletedEvents) },
        )

        Spacer(Modifier.height(16.dp))

        // Agenda — the only group labelled, so its agenda-specific rows are easy
        // to pick out among the otherwise unheadered Appearance settings.
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
            title = stringResource(R.string.settings_agenda_widget_range),
            summary = agendaRangeLabel(state.agendaWidgetRange),
            position = Position.Middle,
            onClick = { showAgendaWidgetRange = true },
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
    }

    if (showTheme) {
        OptionPicker(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = state.themeMode,
            label = { themeLabel(it) },
            onSelect = viewModel::setThemeMode,
            onDismiss = { showTheme = false },
        )
    }
    if (showWeekStart) {
        OptionPicker(
            title = stringResource(R.string.settings_week_start),
            options = WEEK_START_OPTIONS,
            selected = state.weekStart,
            label = { weekStartLabel(it) },
            onSelect = viewModel::setWeekStart,
            onDismiss = { showWeekStart = false },
        )
    }
    if (showAgendaScreenRange) {
        AgendaRangePicker(
            title = stringResource(R.string.settings_agenda_range),
            description = stringResource(R.string.settings_agenda_range_hint),
            selected = state.agendaScreenRange,
            onSelect = viewModel::setAgendaScreenRange,
            onDismiss = { showAgendaScreenRange = false },
        )
    }
    if (showAgendaWidgetRange) {
        AgendaRangePicker(
            title = stringResource(R.string.settings_agenda_widget_range),
            description = stringResource(R.string.settings_agenda_widget_range_hint),
            selected = state.agendaWidgetRange,
            onSelect = viewModel::setAgendaWidgetRange,
            onDismiss = { showAgendaWidgetRange = false },
        )
    }
    if (showTimeFormat) {
        OptionPicker(
            title = stringResource(R.string.settings_time_format),
            options = TimeFormatPref.entries,
            selected = state.timeFormat,
            label = { timeFormatLabel(it) },
            onSelect = viewModel::setTimeFormat,
            onDismiss = { showTimeFormat = false },
        )
    }
    if (showPastEvents) {
        OptionPicker(
            title = stringResource(R.string.settings_past_events),
            options = PastEventDisplay.entries,
            selected = state.pastEventDisplay,
            label = { pastEventDisplayLabel(it) },
            onSelect = viewModel::setPastEventDisplay,
            onDismiss = { showPastEvents = false },
        )
    }
    if (showDefaultView) {
        OptionPicker(
            title = stringResource(R.string.settings_default_view),
            options = IMPLEMENTED_VIEWS,
            selected = state.defaultView,
            label = { stringResource(it.labelRes) },
            onSelect = viewModel::setDefaultView,
            onDismiss = { showDefaultView = false },
        )
    }
}

@Composable
private fun EventFormScreen(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    CollapsingScaffold(
        title = stringResource(R.string.settings_section_event_form),
        onBack = onBack,
    ) {
        Text(
            text = stringResource(R.string.settings_form_fields_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        val fields = EventFormField.entries
        fields.forEachIndexed { index, field ->
            val checked = field in state.defaultFormFields
            GroupedRow(
                title = stringResource(eventFormFieldLabel(field)),
                position = positionOf(index, fields.size),
                // Same icon the field carries in the new-event form, so a toggle
                // is easy to match to the field it controls.
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

        // Auto-focus the title on a new event (issue #10) — on by default, since
        // most events get a title; raising the keyboard saves a tap. Off lets you
        // set the time/calendar first without the keyboard in the way.
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

        // Per-event colour on calendars that publish no colour set (some
        // CalDAV) — off by default, with the honest caveat that the colour may
        // not survive their next sync. Local and palette calendars ignore it.
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
}

/**
 * Ask the system to add the "New event" Quick Settings tile (API 33+). The OS
 * shows its own confirmation dialog and handles the already-added case, so no
 * result handling is needed here.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestAddQsTile(context: Context) {
    val statusBar = context.getSystemService(StatusBarManager::class.java) ?: return
    statusBar.requestAddTileService(
        ComponentName(context, NewEventTileService::class.java),
        context.getString(R.string.qs_tile_new_event_label),
        Icon.createWithResource(context, R.drawable.ic_qs_new_event),
        context.mainExecutor,
    ) { /* result code unused — the system surfaces its own feedback */ }
}

/**
 * Reminder-notifications toggle (v1.4), mirroring the onboarding step.
 * Turning it on re-requests `POST_NOTIFICATIONS` when missing (API 33+) —
 * the pref is set either way; the OS permission is the real gate.
 */
@Composable
private fun NotificationsScreen(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
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
                formatTimeOfDay(context, state.allDayReminderTimeMinutes),
            ),
            position = Position.Bottom,
            onClick = { showAllDayReminderTime = true },
        )

        // Delivery reliability + snooze: both are global reminder-delivery
        // settings, so they sit with the defaults above rather than below the
        // long per-calendar list. Reliability is a soft, optional battery-
        // optimisation exemption (system-settings deep-link, no special
        // permission); shown as live status, reversible at any time.
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
                {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                null
            },
            onClick = { openBatteryOptimizationSettings(context) },
        )

        // Snooze: how long the notification's "Snooze" action defers a reminder.
        GroupedRow(
            title = stringResource(R.string.settings_snooze_duration),
            summary = snoozeDurationLabel(state.snoozeMinutes),
            position = Position.Bottom,
            onClick = { showSnooze = true },
        )

        // Per-calendar overrides: the whole section folds behind one header to
        // keep the screen tidy. Expanded, each writable calendar gets its own
        // expandable card that may keep, drop, or replace the global default —
        // separately for timed and all-day events.
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
                enter = calendarExpandEnter(),
                exit = calendarCollapseExit(),
            ) {
                Column {
                    state.writableCalendars.forEach { calendar ->
                        Spacer(Modifier.height(16.dp))
                        val expanded = calendar.id in expandedCalendars
                        // Calendar card; tapping expands it into a grouped list
                        // of three (the card + the timed and all-day rows).
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
                            enter = calendarExpandEnter(),
                            exit = calendarCollapseExit(),
                        ) {
                            Column {
                                val timed = state.perCalendarReminderOverride.choiceFor(calendar.id)
                                GroupedRow(
                                    title = stringResource(R.string.settings_default_reminder),
                                    summary = calendarOverrideSummary(timed, state.defaultReminderMinutes),
                                    position = Position.Middle,
                                    onClick = { overrideDialog = OverrideTarget(calendar.id, isAllDay = false) },
                                )
                                val allDay = state.perCalendarAllDayReminderOverride.choiceFor(calendar.id)
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
        OptionPicker(
            title = stringResource(R.string.settings_snooze_duration),
            options = SNOOZE_PRESETS,
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
            selected = map.choiceFor(target.calendarId),
            allowInherit = true,
            onSelect = {
                if (target.isAllDay) {
                    viewModel.setCalendarAllDayReminderOverride(target.calendarId, it)
                } else {
                    viewModel.setCalendarReminderOverride(target.calendarId, it)
                }
            },
            onDismiss = { overrideDialog = null },
        )
    }
}

// ---------------------------------------------------------------------------
// Contact special dates (issue #15)
// ---------------------------------------------------------------------------

@Composable
private fun SpecialDatesScreen(
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
        if (hasContactsPermission(context)) {
            viewModel.setSpecialDatesEnabled(true)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    var confirmDisableAll by remember { mutableStateOf(false) }
    var confirmDisableType by remember { mutableStateOf<SpecialDateType?>(null) }
    var editTemplate by remember { mutableStateOf<SpecialDateType?>(null) }

    CollapsingScaffold(
        title = stringResource(R.string.settings_section_special_dates),
        onBack = onBack,
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
            Spacer(Modifier.height(24.dp))
            // Per-type toggles + their title templates.
            SpecialDateType.entries.forEachIndexed { index, type ->
                val on = type in state.types
                GroupedRow(
                    title = stringResource(specialDateTypeLabel(type)),
                    position = if (index == 0) Position.Top else Position.Middle,
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
            }
            GroupedRow(
                title = stringResource(R.string.settings_special_dates_template),
                summary = state.titleTemplates[SpecialDateType.Birthday].orEmpty(),
                position = Position.Bottom,
                onClick = { editTemplate = SpecialDateType.Birthday },
            )

            Spacer(Modifier.height(24.dp))
            GroupedRow(
                title = stringResource(R.string.settings_special_dates_show_age),
                summary = stringResource(R.string.settings_special_dates_show_age_hint),
                position = Position.Top,
                trailing = {
                    Switch(
                        checked = state.showAge,
                        onCheckedChange = viewModel::setSpecialDatesShowAge,
                    )
                },
                onClick = { viewModel.setSpecialDatesShowAge(!state.showAge) },
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
}

@Composable
private fun SpecialDatesDisableDialog(
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_special_dates_disable_title)) },
        text = { Text(message) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_special_dates_disable_confirm))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
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
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_special_dates_template)) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_special_dates_template_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.dialog_save)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
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

private fun hasContactsPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED

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

/** Which calendar + event kind a per-calendar reminder-override dialog targets. */
private data class OverrideTarget(val calendarId: Long, val isAllDay: Boolean)

/** A global default (empty = none) as a picker choice for selection highlighting. */
private fun List<Int>.toReminderChoice(): CalendarReminderOverride =
    if (isEmpty()) CalendarReminderOverride.None else CalendarReminderOverride.Minutes(this)

/** A picked choice as global-default minutes (Inherit isn't offered for globals). */
private fun CalendarReminderOverride.toMinutesList(): List<Int> =
    (this as? CalendarReminderOverride.Minutes)?.minutes ?: emptyList()

/**
 * Whether Calendula is exempt from battery optimisation, re-read on every
 * `ON_RESUME` so the row reflects a change the user just made in system
 * settings without needing to leave and re-enter the screen.
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
 * Take the user straight to Calendula's exemption: the direct
 * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` dialog ("Allow Calendula to ignore
 * battery optimisation?") rather than the full app list they'd have to scroll.
 * Falls back to the optimisation list if the OS refuses the direct intent.
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

/**
 * Lead times offered for the all-day default — day-scale, since a "minutes
 * before midnight" reminder on an all-day event is rarely what's wanted.
 */
private val ALLDAY_REMINDER_PRESETS = listOf(0, 1_440, 2_880, 10_080)

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

/** A minute-of-day formatted in the device's 12/24-hour convention (e.g. "09:00"). */
private fun formatTimeOfDay(context: Context, minutesOfDay: Int): String {
    val time = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
        set(Calendar.MINUTE, minutesOfDay % 60)
    }.time
    return DateFormat.getTimeFormat(context).format(time)
}

/** The stored override for [calendarId], as a picker choice (absent → inherit). */
private fun Map<Long, List<Int>>.choiceFor(calendarId: Long): CalendarReminderOverride = when {
    !containsKey(calendarId) -> CalendarReminderOverride.Inherit
    this.getValue(calendarId).isEmpty() -> CalendarReminderOverride.None
    else -> CalendarReminderOverride.Minutes(this.getValue(calendarId))
}

/** Label for a global-default choice: empty → "None", else the lead times joined. */
@Composable
private fun reminderChoiceLabel(minutes: List<Int>): String {
    if (minutes.isEmpty()) return stringResource(R.string.reminder_none)
    // reminderLeadTimeLabel is @Composable, so resolve each part in a loop rather
    // than a (non-composable) joinToString transform.
    val parts = ArrayList<String>(minutes.size)
    for (m in minutes) parts.add(reminderLeadTimeLabel(m))
    return parts.joinToString(", ")
}

/** Row summary for a calendar: its override, or the inherited global default. */
@Composable
private fun calendarOverrideSummary(
    choice: CalendarReminderOverride,
    globalDefault: List<Int>,
): String = when (choice) {
    CalendarReminderOverride.Inherit ->
        stringResource(R.string.settings_calendar_reminder_inherits, reminderChoiceLabel(globalDefault))
    CalendarReminderOverride.None -> stringResource(R.string.reminder_none)
    is CalendarReminderOverride.Minutes -> reminderChoiceLabel(choice.minutes)
}

// ---------------------------------------------------------------------------
// Shared building blocks
// ---------------------------------------------------------------------------

/**
 * Leading circular icon chip. Colours come from the M3 scheme via a container /
 * on-container token pair, so each accent stays correctly paired across theme,
 * dark mode and dynamic colour.
 */
@Composable
private fun CategoryIcon(icon: ImageVector, accent: ChipAccent) {
    val scheme = MaterialTheme.colorScheme
    val (background, iconColor) = when (accent) {
        ChipAccent.Neutral -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        ChipAccent.Primary -> scheme.primaryContainer to scheme.onPrimaryContainer
        ChipAccent.Tertiary -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(22.dp),
        )
    }
}


private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
    runCatching { context.startActivity(intent) }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    },
)

/** Picker options: "Follow system" first, then Monday…Sunday in ISO order. */
private val WEEK_START_OPTIONS: List<WeekStartPref> =
    listOf(WeekStartPref.Auto) + DayOfWeek.entries.map { WeekStartPref.Day(it) }

@Composable
private fun weekStartLabel(pref: WeekStartPref): String = when (pref) {
    WeekStartPref.Auto -> stringResource(R.string.settings_week_start_auto)
    // Localised full weekday name, so any of the seven days reads naturally
    // without a per-day string resource.
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

/** A small primary-coloured group label, matching the Calendars settings screen. */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun pastEventDisplayLabel(mode: PastEventDisplay): String = stringResource(
    when (mode) {
        PastEventDisplay.SHOW -> R.string.settings_past_events_show
        PastEventDisplay.DIM -> R.string.settings_past_events_dim
        PastEventDisplay.HIDE -> R.string.settings_past_events_hide
    },
)

@Composable
private fun languageLabel(tag: String?): String =
    if (tag == null) stringResource(R.string.settings_language_auto) else AppLanguage.displayName(tag)
