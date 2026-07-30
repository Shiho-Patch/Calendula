package de.jeanlucmakiola.calendula.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarSlideSpec
import de.jeanlucmakiola.floret.components.AboutCard
import de.jeanlucmakiola.floret.components.CollapsingScaffold
import de.jeanlucmakiola.floret.components.GroupedListInset
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.OptionPicker
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.floret.crash.CrashReportDialog
import de.jeanlucmakiola.floret.crash.CrashReporter
import de.jeanlucmakiola.floret.crash.openIssueTracker
import de.jeanlucmakiola.floret.crash.submitCrashReport
import de.jeanlucmakiola.floret.locale.AppLanguage

/** The settings sub-screens reached from the hub's category rows. */
private enum class SettingsSection { Appearance, Views, EventForm, Notifications, SpecialDates, Widgets }

/**
 * Settings hub: three labelled groups of category rows, each opening a
 * sub-screen from its own `*Settings.kt` file (#69). Calendars and Backup open
 * screens hoisted in `CalendarHost`; Language opens a full-screen picker.
 * [onBack] pops the destination.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onManageCalendars: () -> Unit,
    onOpenBackup: () -> Unit,
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
            onOpenBackup = onOpenBackup,
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
        AnimatedVisibility(
            visible = section == SettingsSection.Widgets,
            enter = slideInHorizontally(slideSpec) { it } + fadeIn(),
            exit = slideOutHorizontally(slideSpec) { it } + fadeOut(),
        ) {
            WidgetsScreen(state = state, viewModel = viewModel, onBack = { section = null })
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
    onOpenBackup: () -> Unit,
) {
    CollapsingScaffold(title = stringResource(R.string.settings_title), onBack = onBack, predictiveBack = true) {
        // Card and support row are one grouped block, so the call to action
        // continues the container instead of sitting inside it as a button.
        Box(Modifier.padding(horizontal = GroupedListInset)) { AboutCard() }
        SupportRow()
        Spacer(Modifier.height(16.dp))

        // Each group cycles its chip accents so no two rows in it look alike
        // (see [ChipAccent]).
        SectionHeader(stringResource(R.string.settings_group_look))
        GroupedRow(
            title = stringResource(R.string.settings_section_appearance),
            summary = stringResource(R.string.settings_appearance_subtitle),
            position = Position.Top,
            leading = { CategoryIcon(Icons.Default.Palette, ChipAccent.Secondary) },
            onClick = { onOpenSection(SettingsSection.Appearance) },
        )
        GroupedRow(
            title = stringResource(R.string.settings_section_views),
            summary = stringResource(R.string.settings_views_subtitle),
            position = Position.Middle,
            leading = { CategoryIcon(Icons.Default.SwapVert, ChipAccent.Tertiary) },
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
            position = Position.Bottom,
            leading = { CategoryIcon(Icons.Default.Notifications, ChipAccent.Primary) },
            onClick = { onOpenSection(SettingsSection.Notifications) },
        )

        Spacer(Modifier.height(8.dp))
        SectionHeader(stringResource(R.string.settings_group_data))
        GroupedRow(
            title = stringResource(R.string.settings_section_calendars),
            summary = stringResource(R.string.settings_manage_calendars_hint),
            position = Position.Top,
            leading = { CategoryIcon(Icons.Default.CalendarMonth, ChipAccent.Tertiary) },
            onClick = onManageCalendars,
        )
        GroupedRow(
            title = stringResource(R.string.settings_section_special_dates),
            summary = stringResource(R.string.settings_special_dates_subtitle),
            position = Position.Middle,
            leading = { CategoryIcon(Icons.Default.Cake, ChipAccent.Primary) },
            onClick = { onOpenSection(SettingsSection.SpecialDates) },
        )
        // Export/import gets its own top-level entry (#69); the calendar
        // manager keeps a pointer row to here.
        GroupedRow(
            title = stringResource(R.string.settings_section_backup),
            summary = stringResource(R.string.settings_backup_subtitle),
            position = Position.Bottom,
            leading = { CategoryIcon(Icons.Default.Backup, ChipAccent.Secondary) },
            onClick = onOpenBackup,
        )

        Spacer(Modifier.height(8.dp))
        SectionHeader(stringResource(R.string.settings_group_app))
        GroupedRow(
            title = stringResource(R.string.settings_section_widgets),
            summary = stringResource(R.string.settings_widgets_subtitle),
            position = Position.Top,
            leading = { CategoryIcon(Icons.Default.Widgets, ChipAccent.Tertiary) },
            onClick = { onOpenSection(SettingsSection.Widgets) },
        )
        LanguageRow(position = Position.Middle)
        ReportProblemRow(position = Position.Bottom)

        // Source, licence and privacy sit at the bottom as reference material
        // rather than inside the About card.
        Spacer(Modifier.height(8.dp))
        SectionHeader(stringResource(R.string.settings_group_about))
        AboutLinkRow(
            label = stringResource(R.string.settings_about_source),
            url = stringResource(R.string.about_source_url),
            icon = ImageVector.vectorResource(R.drawable.ic_gitea),
            position = Position.Top,
        )
        AboutLinkRow(
            label = stringResource(R.string.settings_license),
            url = stringResource(R.string.about_license_url),
            icon = Icons.Default.Gavel,
            position = Position.Middle,
        )
        AboutLinkRow(
            label = stringResource(R.string.settings_about_privacy),
            url = stringResource(R.string.about_privacy_url),
            icon = Icons.Default.PrivacyTip,
            position = Position.Bottom,
        )

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
        leading = { CategoryIcon(Icons.Default.Language, ChipAccent.Secondary) },
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
private fun languageLabel(tag: String?): String =
    if (tag == null) stringResource(R.string.settings_language_auto) else AppLanguage.displayName(tag)

@Composable
private fun AboutCard() {
    // Layout lives in floret-kit (components.AboutCard); Calendula supplies
    // its own logo and author. Support is the [SupportRow] joined below.
    AboutCard(
        logo = { AppLogo() },
        appName = stringResource(R.string.app_name),
        author = stringResource(R.string.settings_about_author),
        primaryLinks = emptyList(),
        position = Position.Top,
    )
}

/** "Support development", as the closing row of the About card's group. */
@Composable
private fun SupportRow() {
    val context = LocalContext.current
    val url = stringResource(R.string.about_support_url)
    GroupedRow(
        title = stringResource(R.string.settings_about_support),
        position = Position.Bottom,
        leading = { CategoryIcon(Icons.Default.Favorite, ChipAccent.Primary) },
        onClick = { openUrl(context, url) },
    )
}

/**
 * One reference link at the foot of the hub (source, licence, privacy). The
 * privacy policy has to stay reachable from inside the app, not only from the
 * store listing.
 */
@Composable
private fun AboutLinkRow(label: String, url: String, icon: ImageVector, position: Position) {
    val context = LocalContext.current
    GroupedRow(
        title = label,
        position = position,
        leading = { CategoryIcon(icon, ChipAccent.Neutral) },
        onClick = { openUrl(context, url) },
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
 * The app icon as a rounded chip: the launcher mark over its background colour,
 * oversized and clipped the way a launcher mask would. Sized just past the two
 * text lines beside it; [MARK_OVERSCAN] holds the crop as it shrinks.
 */
@Composable
private fun AppLogo() {
    Box(
        modifier = Modifier
            .size(LOGO_SIZE)
            .clip(RoundedCornerShape(16.dp))
            .background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = stringResource(R.string.settings_about_logo_desc),
            modifier = Modifier.requiredSize(LOGO_SIZE * MARK_OVERSCAN),
        )
    }
}

/** Roughly the height of the app name and author lines it sits beside. */
private val LOGO_SIZE = 56.dp

/**
 * How far the mark overruns its chip. An adaptive icon's foreground carries a
 * wide safe margin, so at 1:1 it would sit small and lost; launchers crop it too.
 */
private const val MARK_OVERSCAN = 1.5f
