package de.jeanlucmakiola.calendula.ui.settings

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.FilledTonalButton
import de.jeanlucmakiola.floret.locale.AppLanguage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
import de.jeanlucmakiola.calendula.data.appname.LauncherName
import de.jeanlucmakiola.calendula.data.contacts.hasContactsPermission
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
import de.jeanlucmakiola.floret.reminders.ReminderOverride
import de.jeanlucmakiola.floret.reminders.reminderOverrideFor
import de.jeanlucmakiola.calendula.data.prefs.ThemeMode
import de.jeanlucmakiola.calendula.data.prefs.TimeFormatPref
import de.jeanlucmakiola.calendula.data.prefs.WeekStartPref
import de.jeanlucmakiola.calendula.data.prefs.resolveFirstDay
import de.jeanlucmakiola.calendula.domain.EventFormField
import de.jeanlucmakiola.calendula.domain.contacts.SpecialDateType
import de.jeanlucmakiola.calendula.qs.NewEventTileService
import de.jeanlucmakiola.floret.crash.CrashReportDialog
import de.jeanlucmakiola.floret.crash.CrashReporter
import de.jeanlucmakiola.floret.crash.openIssueTracker
import de.jeanlucmakiola.floret.crash.submitCrashReport
import de.jeanlucmakiola.calendula.domain.FontRole
import de.jeanlucmakiola.floret.identity.collapseExit
import de.jeanlucmakiola.floret.identity.expandEnter
import de.jeanlucmakiola.calendula.ui.common.AgendaRangePicker
import de.jeanlucmakiola.calendula.ui.common.PickerDescription
import de.jeanlucmakiola.floret.components.FullScreenPicker
import de.jeanlucmakiola.calendula.ui.common.agendaRangeLabel
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.floret.components.AboutCard
import de.jeanlucmakiola.floret.components.AboutLink
import de.jeanlucmakiola.floret.components.CollapsingScaffold
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.InlineTextField
import de.jeanlucmakiola.calendula.ui.common.IMPLEMENTED_VIEWS
import de.jeanlucmakiola.calendula.ui.common.QuickSwitchConfig
import de.jeanlucmakiola.calendula.ui.month.labelRes
import de.jeanlucmakiola.floret.components.ReorderableColumn
import de.jeanlucmakiola.floret.components.ReorderableRowHeight
import de.jeanlucmakiola.calendula.ui.common.icon
import de.jeanlucmakiola.calendula.ui.common.labelRes
import de.jeanlucmakiola.calendula.ui.common.CalendarColorChip
import de.jeanlucmakiola.floret.components.OptionPicker
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.calendula.ui.common.REMINDER_PRESETS
import de.jeanlucmakiola.calendula.ui.common.ReminderDefaultPicker
import de.jeanlucmakiola.calendula.ui.common.TimePickerAlert
import de.jeanlucmakiola.floret.components.SelectedCheck
import de.jeanlucmakiola.floret.components.positionOf
import de.jeanlucmakiola.calendula.ui.common.reminderLeadTimeLabel
import de.jeanlucmakiola.calendula.ui.common.SnoozeDurationPicker
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarSlideSpec
import de.jeanlucmakiola.floret.locale.currentLocale
import de.jeanlucmakiola.calendula.ui.common.eventFormFieldIcon
import de.jeanlucmakiola.calendula.ui.common.eventFormFieldLabel
import de.jeanlucmakiola.calendula.ui.theme.BundledFont
import de.jeanlucmakiola.calendula.ui.theme.FONT_CUSTOM_TOKEN
import de.jeanlucmakiola.calendula.ui.theme.FONT_SYSTEM_TOKEN
import de.jeanlucmakiola.calendula.ui.theme.resolveFontFamily
import de.jeanlucmakiola.calendula.widget.WidgetSize
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import java.time.format.TextStyle as JavaTextStyle
import java.util.Calendar

/** The settings sub-screens reached from the hub's category rows. */
private enum class SettingsSection { Appearance, Views, EventForm, Notifications, SpecialDates }

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
 * [CalendarHost]; Language opens a full-screen picker; About is a card
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
            visible = section == SettingsSection.Views,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(),
            exit = slideOutHorizontally(slideSpec) { it } + fadeOut(),
        ) {
            ViewsScreen(state = state, viewModel = viewModel, onBack = { section = null })
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
            NotificationsScreen(
                state = state,
                viewModel = viewModel,
                onBack = { section = null },
                onOpenSpecialDates = { section = SettingsSection.SpecialDates },
            )
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
    CollapsingScaffold(title = stringResource(R.string.settings_title), onBack = onBack, predictiveBack = true) {
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
            title = stringResource(R.string.settings_section_views),
            summary = stringResource(R.string.settings_views_subtitle),
            position = Position.Middle,
            leading = { CategoryIcon(Icons.Default.SwapVert, ChipAccent.Neutral) },
            onClick = { onOpenSection(SettingsSection.Views) },
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
    val options = remember { listOf<String?>(null) + AppLanguage.supportedTags(context, R.xml.locales_config) }

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
            predictiveBack = true,
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
    // The card layout lives in floret-kit (components.AboutCard); Calendula
    // supplies its own logo, author and the source / licence / privacy / support
    // links. The privacy policy has to be reachable from inside the app, not just
    // from the store listing, because Calendula touches calendar and contact data.
    AboutCard(
        logo = { AppLogo() },
        appName = stringResource(R.string.app_name),
        author = stringResource(R.string.settings_about_author),
        primaryLinks = listOf(
            AboutLink(
                icon = ImageVector.vectorResource(R.drawable.ic_gitea),
                label = stringResource(R.string.settings_about_source),
                url = stringResource(R.string.about_source_url),
            ),
            AboutLink(
                icon = Icons.Default.Gavel,
                label = stringResource(R.string.settings_license),
                url = stringResource(R.string.about_license_url),
            ),
            AboutLink(
                icon = Icons.Default.PrivacyTip,
                label = stringResource(R.string.settings_about_privacy),
                url = stringResource(R.string.about_privacy_url),
            ),
        ),
        highlightLink = AboutLink(
            icon = Icons.Default.Favorite,
            label = stringResource(R.string.settings_about_support),
            url = stringResource(R.string.about_support_url),
        ),
    )
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
    var showWidgetSize by remember { mutableStateOf(false) }
    var showPastEvents by remember { mutableStateOf(false) }
    var showBrandFont by remember { mutableStateOf(false) }
    var showPlainFont by remember { mutableStateOf(false) }
    var showAppName by remember { mutableStateOf(false) }

    val fonts by viewModel.fontState.collectAsStateWithLifecycle()
    val launcherName by viewModel.launcherName.collectAsStateWithLifecycle()
    // A picked file that didn't parse as a font: tell the user and keep the old choice.
    val context = LocalContext.current
    val importFailedMessage = stringResource(R.string.settings_font_import_failed)
    LaunchedEffect(Unit) {
        viewModel.fontImportFailed.collect {
            Toast.makeText(context, importFailedMessage, Toast.LENGTH_LONG).show()
        }
    }

    CollapsingScaffold(
        title = stringResource(R.string.settings_section_appearance),
        onBack = onBack,
        predictiveBack = true,
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
            position = Position.Middle,
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
        GroupedRow(
            title = stringResource(R.string.settings_soften_colors),
            summary = stringResource(R.string.settings_soften_colors_summary),
            position = Position.Bottom,
            trailing = {
                Switch(
                    checked = state.softenColors,
                    onCheckedChange = viewModel::setSoftenColors,
                )
            },
            onClick = { viewModel.setSoftenColors(!state.softenColors) },
        )

        Spacer(Modifier.height(16.dp))

        // Fonts — the two Material typeface roles, each independently choosable
        // (issue #19). Headings = brand (display/headline); body = plain
        // (title/body/label). Both default to the system typeface.
        GroupedRow(
            title = stringResource(R.string.settings_font_headings),
            summary = fontLabel(fonts.brand),
            position = Position.Top,
            onClick = { showBrandFont = true },
        )
        GroupedRow(
            title = stringResource(R.string.settings_font_body),
            summary = fontLabel(fonts.plain),
            position = Position.Bottom,
            onClick = { showPlainFont = true },
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
            title = stringResource(R.string.settings_week_start),
            summary = weekStartLabel(state.weekStart),
            position = Position.Middle,
            onClick = { showWeekStart = true },
        )
        GroupedRow(
            title = stringResource(R.string.settings_week_numbers),
            summary = stringResource(R.string.settings_week_numbers_summary),
            position = Position.Middle,
            trailing = {
                Switch(
                    checked = state.showWeekNumbers,
                    onCheckedChange = viewModel::setShowWeekNumbers,
                )
            },
            onClick = { viewModel.setShowWeekNumbers(!state.showWeekNumbers) },
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
            title = stringResource(R.string.settings_widget_size),
            summary = widgetSizeLabel(state.widgetSize),
            position = Position.Middle,
            onClick = { showWidgetSize = true },
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

        Spacer(Modifier.height(16.dp))

        // App name — chooses the launcher label between "Calendula" and "Calendar"
        // (issue #44). Own group: it's a launcher/system concern, not calendar
        // formatting. A sub-page chooser (not a switch), matching the app's other
        // "choose one" settings and leaving room for more names later.
        GroupedRow(
            title = stringResource(R.string.settings_app_name),
            summary = launcherNameLabel(launcherName),
            position = Position.Alone,
            onClick = { showAppName = true },
        )
    }

    if (showAppName) {
        FullScreenPicker(
            title = stringResource(R.string.settings_app_name),
            onDismiss = { showAppName = false },
            predictiveBack = true,
        ) {
            // Show both names as launcher-mark previews so the user sees what
            // they'd switch to, not just the current state. Tapping applies
            // immediately and highlights — the picker stays open so the change is
            // visible; back exits.
            Text(
                text = stringResource(R.string.settings_app_name_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LauncherName.entries.forEach { option ->
                    AppNameOptionCard(
                        name = option,
                        selected = launcherName == option,
                        onClick = { viewModel.setLauncherName(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    if (showTheme) {
        OptionPicker(
            title = stringResource(R.string.settings_theme),
            predictiveBack = true,
            options = ThemeMode.entries,
            selected = state.themeMode,
            label = { themeLabel(it) },
            onSelect = viewModel::setThemeMode,
            onDismiss = { showTheme = false },
        )
    }
    if (showBrandFont) {
        FontPicker(
            title = stringResource(R.string.settings_font_headings),
            role = FontRole.BRAND,
            selected = fonts.brand,
            stamp = fonts.brandStamp,
            onSelect = { viewModel.setFont(FontRole.BRAND, it) },
            onImport = { viewModel.importCustomFont(FontRole.BRAND, it) },
            onDismiss = { showBrandFont = false },
        )
    }
    if (showPlainFont) {
        FontPicker(
            title = stringResource(R.string.settings_font_body),
            role = FontRole.PLAIN,
            selected = fonts.plain,
            stamp = fonts.plainStamp,
            onSelect = { viewModel.setFont(FontRole.PLAIN, it) },
            onImport = { viewModel.importCustomFont(FontRole.PLAIN, it) },
            onDismiss = { showPlainFont = false },
        )
    }
    if (showWeekStart) {
        OptionPicker(
            title = stringResource(R.string.settings_week_start),
            predictiveBack = true,
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
    if (showWidgetSize) {
        OptionPicker(
            title = stringResource(R.string.settings_widget_size),
            header = { PickerDescription(stringResource(R.string.settings_widget_size_hint)) },
            predictiveBack = true,
            options = WidgetSize.entries,
            selected = state.widgetSize,
            label = { widgetSizeLabel(it) },
            onSelect = viewModel::setWidgetSize,
            onDismiss = { showWidgetSize = false },
        )
    }
    if (showTimeFormat) {
        OptionPicker(
            title = stringResource(R.string.settings_time_format),
            predictiveBack = true,
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
            predictiveBack = true,
            options = IMPLEMENTED_VIEWS,
            selected = state.defaultView,
            label = { stringResource(it.labelRes) },
            onSelect = viewModel::setDefaultView,
            onDismiss = { showDefaultView = false },
        )
    }
}

/**
 * Views (#24): reorder the top-bar quick-switch cycle and choose which views it
 * steps through, plus reorder the navigation-drawer list. Two independent lists —
 * a view disabled in the quick-switch cycle is still reachable from the drawer,
 * which always lists every view. The switch needs at least two targets, so the
 * last [QuickSwitchConfig.MIN_ENABLED] enabled views can't be turned off.
 */
@Composable
private fun ViewsScreen(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    var showMonthStyle by remember { mutableStateOf(false) }

    CollapsingScaffold(
        title = stringResource(R.string.settings_section_views),
        onBack = onBack,
        predictiveBack = true,
    ) {
        val config = state.quickSwitchConfig

        // Per-view layout, above the cross-view switcher/order settings below.
        SectionHeader(stringResource(R.string.settings_month_header))
        GroupedRow(
            title = stringResource(R.string.settings_month_view_style),
            summary = stringResource(state.monthViewStyle.labelRes),
            position = Position.Alone,
            onClick = { showMonthStyle = true },
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
}

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

/** Muted supporting text under a [SectionHeader], matching the form-fields hint. */
@Composable
private fun SettingsHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
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
        predictiveBack = true,
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
                { SelectedCheck() }
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
                enter = expandEnter(),
                exit = collapseExit(),
            ) {
                Column {
                    state.writableCalendars.forEach { calendar ->
                        Spacer(Modifier.height(16.dp))
                        // A contact special-dates calendar owns its reminders in
                        // its own section — link there instead of an override.
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
private fun List<Int>.toReminderChoice(): ReminderOverride =
    if (isEmpty()) ReminderOverride.None else ReminderOverride.Minutes(this)

/** A picked choice as global-default minutes (Inherit isn't offered for globals). */
private fun ReminderOverride.toMinutesList(): List<Int> =
    (this as? ReminderOverride.Minutes)?.minutes ?: emptyList()

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

/** Label for a global-default choice: empty → "None", else the lead times joined. */
@Composable
private fun reminderChoiceLabel(minutes: List<Int>): String {
    if (minutes.isEmpty()) return stringResource(R.string.reminder_none)
    return minutes.map { reminderLeadTimeLabel(it) }.joinToString(", ")
}

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

/** The display name for a launcher-label choice (issue #44). */
@Composable
private fun launcherNameLabel(name: LauncherName): String = stringResource(
    when (name) {
        LauncherName.CALENDULA -> R.string.app_name
        LauncherName.CALENDAR -> R.string.app_name_calendar_alias
    },
)

/**
 * One selectable launcher-name preview in the App name picker (issue #44): the
 * app's launcher mark over the name, framed as a card. The active one carries a
 * primary border, a tinted container and a check; tapping selects it. The mark
 * is the same for both — only the label changes — so the card previews exactly
 * what the home screen will read.
 */
@Composable
private fun AppNameOptionCard(
    name: LauncherName,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    Column(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = shape)
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The adaptive launcher mark, reconstructed as a squircle (as in the
        // onboarding BrandHero) so it renders identically everywhere.
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colorResource(R.color.ic_launcher_background)),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = launcherNameLabel(name),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        // Selection indicator: a filled check when active, an empty ring otherwise.
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .then(
                    if (selected) {
                        Modifier
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    },
)

/** The summary label for a stored font token (issue #19). */
@Composable
private fun fontLabel(token: String): String = when (token) {
    FONT_SYSTEM_TOKEN -> stringResource(R.string.settings_font_system)
    FONT_CUSTOM_TOKEN -> stringResource(R.string.settings_font_custom_selected)
    else -> BundledFont.fromToken(token)?.let { stringResource(it.labelRes) }
        ?: stringResource(R.string.settings_font_system)
}

/**
 * MIME types offered to the document picker so it lists only font files. Covers
 * the modern `font/` types plus the legacy `application/` font aliases some
 * providers still report. Anything that slips through is still validated by
 * [de.jeanlucmakiola.calendula.data.fonts.CustomFontStore] before use.
 */
private val FONT_PICKER_MIME_TYPES = arrayOf(
    "font/ttf",
    "font/otf",
    "font/sfnt",
    "font/collection",
    "application/x-font-ttf",
    "application/x-font-otf",
    "application/font-sfnt",
    "application/vnd.ms-opentype",
)

/**
 * Full-screen font chooser for one [FontRole]: the system default, each bundled
 * font (previewed in its own face), and "Choose file…" which opens the system
 * picker to load a .ttf/.otf. Selecting a system/bundled option applies at once;
 * a file is validated and imported by the caller, switching to the custom font
 * on success.
 */
@Composable
private fun FontPicker(
    title: String,
    role: FontRole,
    selected: String,
    stamp: Int,
    onSelect: (String) -> Unit,
    onImport: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onImport(uri)
            onDismiss()
        }
    }

    // System default + the bundled fonts + the "Choose file…" row.
    val rowCount = BundledFont.entries.size + 2
    val isCustom = selected == FONT_CUSTOM_TOKEN
    // Resolving the custom face stats the disk and builds a fresh FontFamily, so
    // memoise it; re-keyed on [stamp] (bumped on re-import) so a replaced file
    // refreshes the preview while plain recompositions reuse the cached family.
    val customPreview = remember(role, isCustom, stamp) {
        if (isCustom) resolveFontFamily(FONT_CUSTOM_TOKEN, role, context) else null
    }

    FullScreenPicker(title = title, onDismiss = onDismiss) {
        FontOptionRow(
            label = stringResource(R.string.settings_font_system),
            preview = FontFamily.Default,
            selected = selected == FONT_SYSTEM_TOKEN,
            position = positionOf(0, rowCount),
            onClick = {
                onSelect(FONT_SYSTEM_TOKEN)
                onDismiss()
            },
        )
        BundledFont.entries.forEachIndexed { index, font ->
            FontOptionRow(
                label = stringResource(font.labelRes),
                preview = font.family,
                selected = selected == font.token,
                position = positionOf(index + 1, rowCount),
                onClick = {
                    onSelect(font.token)
                    onDismiss()
                },
            )
        }
        FontOptionRow(
            label = if (isCustom) {
                stringResource(R.string.settings_font_custom_selected)
            } else {
                stringResource(R.string.settings_font_choose_file)
            },
            // A loaded font previews in its own face; otherwise show an upload cue.
            preview = customPreview,
            leadingIcon = if (isCustom) null else Icons.Default.UploadFile,
            selected = isCustom,
            position = positionOf(rowCount - 1, rowCount),
            onClick = { launcher.launch(FONT_PICKER_MIME_TYPES) },
        )
    }
}

/**
 * One row in the [FontPicker]: the font's name, a leading "Ag" sample rendered in
 * the option's own [preview] face (or an [leadingIcon] cue when there's nothing
 * to preview), and a check when it's the current selection.
 */
@Composable
private fun FontOptionRow(
    label: String,
    preview: FontFamily?,
    selected: Boolean,
    position: Position,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
) {
    GroupedRow(
        title = label,
        position = position,
        selected = selected,
        leading = {
            if (preview != null) {
                Text(
                    text = "Ag",
                    fontFamily = preview,
                    style = MaterialTheme.typography.titleLarge,
                )
            } else if (leadingIcon != null) {
                Icon(imageVector = leadingIcon, contentDescription = null)
            }
        },
        trailing = if (selected) {
            { SelectedCheck() }
        } else {
            null
        },
        onClick = onClick,
    )
}

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
private fun widgetSizeLabel(size: WidgetSize): String = stringResource(
    when (size) {
        WidgetSize.SMALL -> R.string.settings_widget_size_small
        WidgetSize.MEDIUM -> R.string.settings_widget_size_medium
        WidgetSize.LARGE -> R.string.settings_widget_size_large
        WidgetSize.EXTRA_LARGE -> R.string.settings_widget_size_extra_large
    },
)

@Composable
private fun languageLabel(tag: String?): String =
    if (tag == null) stringResource(R.string.settings_language_auto) else AppLanguage.displayName(tag)
