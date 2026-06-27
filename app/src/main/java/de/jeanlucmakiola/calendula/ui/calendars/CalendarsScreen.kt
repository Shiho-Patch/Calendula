package de.jeanlucmakiola.calendula.ui.calendars

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.ui.common.CALENDAR_COLOR_PALETTE
import de.jeanlucmakiola.calendula.ui.common.CalendarColorChip
import de.jeanlucmakiola.calendula.ui.common.calendarCollapseExit
import de.jeanlucmakiola.calendula.ui.common.calendarExpandEnter
import de.jeanlucmakiola.calendula.ui.common.predictiveBack
import de.jeanlucmakiola.calendula.ui.common.CollapsingScaffold
import de.jeanlucmakiola.calendula.ui.common.ColorSwatchRow
import de.jeanlucmakiola.calendula.ui.common.GroupedRow
import de.jeanlucmakiola.calendula.ui.common.InlineTextField
import de.jeanlucmakiola.calendula.ui.common.Position
import de.jeanlucmakiola.calendula.ui.common.pastelize
import java.time.LocalDate

/** Sentinel [editorId] meaning "the editor is composing a new calendar". */
private const val NEW_CALENDAR_ID = Long.MIN_VALUE

/**
 * Calendar manager (reached from Settings). Lists the app's own device-only
 * calendars with create / rename / recolor / delete (via a full-screen editor),
 * and lists synced calendars read-only with a per-account "manage in the source
 * app" deep-link — the app never touches a synced calendar's server. A
 * full-screen destination; [onBack] pops it.
 */
@Composable
fun CalendarsScreen(
    onBack: () -> Unit,
    viewModel: CalendarsViewModel = hiltViewModel(),
) {
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()
    val disabledIds by viewModel.disabledCalendarIds.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val backupResult by viewModel.backupResult.collectAsStateWithLifecycle()

    // null = list; NEW_CALENDAR_ID = create; any other id = edit that calendar.
    // [editorSession] bumps on every open so the editor's field state resets for
    // a fresh open while still surviving configuration changes within one open.
    var editorId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editorSession by rememberSaveable { mutableStateOf(0) }

    if (editorId != null) {
        val editing = calendars.firstOrNull { it.id == editorId }
        CalendarEditor(
            sessionKey = editorSession,
            isNew = editorId == NEW_CALENDAR_ID,
            initialName = editing?.displayName.orEmpty(),
            initialColor = editing?.color ?: CALENDAR_COLOR_PALETTE.first(),
            initialDescription = editing?.description.orEmpty(),
            onSave = { name, color, description ->
                val id = editorId
                if (id == null || id == NEW_CALENDAR_ID) {
                    viewModel.createCalendar(name, color, description)
                } else {
                    viewModel.updateCalendar(id, name, color, description)
                }
                editorId = null
            },
            onDelete = {
                editorId?.takeIf { it != NEW_CALENDAR_ID }?.let(viewModel::deleteCalendar)
                editorId = null
            },
            onClose = { editorId = null },
        )
    } else {
        CalendarsList(
            local = calendars.filter { it.isLocal },
            synced = calendars.filterNot { it.isLocal },
            disabledIds = disabledIds,
            error = error,
            onConsumeError = viewModel::consumeError,
            backupResult = backupResult,
            onExportBackup = viewModel::exportBackup,
            onConsumeBackupResult = viewModel::consumeBackupResult,
            onBack = onBack,
            onAdd = { editorSession++; editorId = NEW_CALENDAR_ID },
            onEdit = { calendar -> editorSession++; editorId = calendar.id },
            onSetDisabled = viewModel::setDisabled,
            onSetAccountDisabled = viewModel::setAccountDisabled,
        )
    }
}

@Composable
private fun CalendarsList(
    local: List<CalendarSource>,
    synced: List<CalendarSource>,
    disabledIds: Set<Long>,
    error: Boolean,
    onConsumeError: () -> Unit,
    backupResult: BackupResult?,
    onExportBackup: (android.net.Uri) -> Unit,
    onConsumeBackupResult: () -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (CalendarSource) -> Unit,
    onSetDisabled: (Long, Boolean) -> Unit,
    onSetAccountDisabled: (Collection<Long>, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    // Accounts the user has folded shut; empty = all expanded (keeps every
    // calendar visible by default, the section is collapsible for tidiness).
    var collapsedAccounts by remember { mutableStateOf(emptySet<String>()) }
    var localExpanded by remember { mutableStateOf(true) }
    val writeErrorText = stringResource(R.string.calendars_write_error)

    LaunchedEffect(error) {
        if (error) {
            snackbarHostState.showSnackbar(writeErrorText)
            onConsumeError()
        }
    }

    // SAF "create document" target for the backup file. The picked Uri is handed
    // to the VM to stream the .ics into.
    val createBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/calendar"),
    ) { uri -> uri?.let(onExportBackup) }

    val backupFailedText = stringResource(R.string.calendars_backup_failed)
    LaunchedEffect(backupResult) {
        when (val r = backupResult) {
            is BackupResult.Success -> {
                snackbarHostState.showSnackbar(
                    context.resources.getQuantityString(
                        R.plurals.calendars_backup_done, r.eventCount, r.eventCount,
                    ),
                )
                onConsumeBackupResult()
            }
            BackupResult.Failure -> {
                snackbarHostState.showSnackbar(backupFailedText)
                onConsumeBackupResult()
            }
            null -> Unit
        }
    }

    CollapsingScaffold(
        title = stringResource(R.string.calendars_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        // What the per-calendar / per-account switches below actually do.
        HintText(stringResource(R.string.calendars_disable_hint))

        // Local (device-only) calendars — one collapsible group. The header's
        // "+" adds a calendar; the switch enables/disables them all at once;
        // tapping a calendar row opens its editor.
        val localDisabled = local.isNotEmpty() && local.all { it.id in disabledIds }
        CalendarGroup(
            title = stringResource(R.string.calendars_local_header),
            expanded = localExpanded,
            headerDisabled = localDisabled,
            leading = { Box(dimIf(localDisabled)) { LeadingAvatar(Icons.Default.PhoneAndroid) } },
            manageIcon = Icons.Default.Add,
            manageLabel = stringResource(R.string.calendars_add),
            onManage = onAdd,
            manageConnectsBelow = local.isNotEmpty(),
            onToggleExpand = { localExpanded = !localExpanded },
            showToggleAll = local.isNotEmpty(),
            allEnabled = local.none { it.id in disabledIds },
            toggleAllLabel = stringResource(
                R.string.calendars_account_toggle_all_a11y,
                stringResource(R.string.calendars_local_header),
            ),
            onToggleAll = { enabled -> onSetAccountDisabled(local.map { it.id }, !enabled) },
        ) {
            if (local.isEmpty()) {
                HintText(stringResource(R.string.calendars_local_empty))
            } else {
                local.forEachIndexed { index, calendar ->
                    val disabled = calendar.id in disabledIds
                    GroupedRow(
                        title = calendar.displayName,
                        summary = calendar.description,
                        position = if (index == local.lastIndex) Position.Bottom else Position.Middle,
                        container = MaterialTheme.colorScheme.surfaceContainerHighest,
                        dimmed = disabled,
                        leading = { CalendarColorChip(calendar.color, dimIf(disabled)) },
                        trailing = {
                            EnableSwitch(
                                calendarName = calendar.displayName,
                                enabled = !disabled,
                                onToggle = { enabled -> onSetDisabled(calendar.id, !enabled) },
                            )
                        },
                        onClick = { onEdit(calendar) },
                    )
                }
            }
        }

        // Backup — local calendars have no sync, so a .ics export is their only
        // safety net. Offered only when there is something to back up.
        if (local.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionHeader(stringResource(R.string.calendars_backup_header))
            HintText(stringResource(R.string.calendars_backup_hint))
            GroupedRow(
                title = stringResource(R.string.calendars_backup_action),
                position = Position.Alone,
                leading = { LeadingAvatar(Icons.Default.FileDownload) },
                onClick = {
                    runCatching { createBackup.launch("calendula-backup-${LocalDate.now()}.ics") }
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        // Synced calendars — read-only, grouped by account. Each account is a
        // collapsible group whose header opens the source app (icon) and toggles
        // all of its calendars at once (switch).
        SectionHeader(stringResource(R.string.calendars_synced_header))
        HintText(stringResource(R.string.calendars_synced_hint))
        synced
            .groupBy { it.accountName.ifBlank { it.accountType } }
            .forEach { (account, cals) ->
                val expanded = account !in collapsedAccounts
                val accountType = cals.first().accountType
                val accountDisabled = cals.all { it.id in disabledIds }
                Spacer(Modifier.height(16.dp))
                CalendarGroup(
                    title = account,
                    expanded = expanded,
                    headerDisabled = accountDisabled,
                    leading = { Box(dimIf(accountDisabled)) { SourceLogo(accountType) } },
                    manageIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    manageLabel = stringResource(R.string.calendars_manage_account_a11y, account),
                    onManage = {
                        runCatching { context.startActivity(sourceAppIntent(context, accountType)) }
                    },
                    manageConnectsBelow = true,
                    onToggleExpand = {
                        collapsedAccounts = if (expanded) {
                            collapsedAccounts + account
                        } else {
                            collapsedAccounts - account
                        }
                    },
                    showToggleAll = true,
                    allEnabled = cals.none { it.id in disabledIds },
                    toggleAllLabel = stringResource(R.string.calendars_account_toggle_all_a11y, account),
                    onToggleAll = { enabled -> onSetAccountDisabled(cals.map { it.id }, !enabled) },
                ) {
                    cals.forEachIndexed { index, calendar ->
                        val disabled = calendar.id in disabledIds
                        GroupedRow(
                            title = calendar.displayName,
                            position = if (index == cals.lastIndex) Position.Bottom else Position.Middle,
                            container = MaterialTheme.colorScheme.surfaceContainerHighest,
                            dimmed = disabled,
                            leading = { CalendarColorChip(calendar.color, dimIf(disabled)) },
                            trailing = {
                                EnableSwitch(
                                    calendarName = calendar.displayName,
                                    enabled = !disabled,
                                    onToggle = { enabled -> onSetDisabled(calendar.id, !enabled) },
                                )
                            },
                        )
                    }
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarEditor(
    sessionKey: Int,
    isNew: Boolean,
    initialName: String,
    initialColor: Int,
    initialDescription: String,
    onSave: (name: String, color: Int, description: String?) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    var name by rememberSaveable(sessionKey) { mutableStateOf(initialName) }
    var color by rememberSaveable(sessionKey) { mutableStateOf(initialColor) }
    var description by rememberSaveable(sessionKey) { mutableStateOf(initialDescription) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dark = isSystemInDarkTheme()

    Scaffold(
        modifier = Modifier
            .predictiveBack(onBack = onClose)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (isNew) R.string.calendars_new_title
                            else R.string.calendars_edit_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.event_edit_close),
                        )
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.event_detail_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    // Filled save button, matching the event editor's top bar.
                    Button(
                        onClick = {
                            onSave(name.trim(), color, description.trim().ifEmpty { null })
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Text(stringResource(R.string.event_edit_save))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditorCard(icon = Icons.Default.CalendarMonth, iconTint = pastelize(color, dark)) {
                InlineTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringResource(R.string.calendars_name_label),
                    textStyle = MaterialTheme.typography.titleLarge,
                    capitalization = KeyboardCapitalization.Sentences,
                )
            }
            EditorCard(
                icon = Icons.Default.Palette,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                iconAtTop = true,
            ) {
                Text(
                    text = stringResource(R.string.calendars_color_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                ColorSwatchRow(
                    colors = CALENDAR_COLOR_PALETTE,
                    selected = color,
                    onSelect = { color = it },
                    dark = dark,
                )
            }
            EditorCard(
                icon = Icons.AutoMirrored.Filled.Notes,
                iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                iconAtTop = true,
            ) {
                InlineTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = stringResource(R.string.calendars_description_hint),
                    singleLine = false,
                    minLines = 2,
                    capitalization = KeyboardCapitalization.Sentences,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.calendars_delete_confirm_title)) },
            text = {
                Text(stringResource(R.string.calendars_delete_confirm_message, initialName))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text(
                        stringResource(R.string.event_detail_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}

/**
 * The per-row enable/disable control. Checked = the calendar is shown in the
 * app; unchecking disables it (events, filters and pickers all drop it) without
 * touching any provider data. Carries its own content description so the toggle
 * is self-describing to screen readers even on a dimmed row.
 */
@Composable
private fun EnableSwitch(
    calendarName: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val label = stringResource(R.string.calendars_show_in_app_a11y, calendarName)
    Switch(
        checked = enabled,
        onCheckedChange = onToggle,
        modifier = Modifier.semantics { contentDescription = label },
    )
}

/** Fade a leading element to the M3 disabled emphasis when [disabled]. */
private fun dimIf(disabled: Boolean): Modifier =
    if (disabled) Modifier.alpha(0.38f) else Modifier

/** Tonal field card matching the event editor's design (icon + content). */
@Composable
private fun EditorCard(
    icon: ImageVector,
    iconTint: Color,
    iconAtTop: Boolean = false,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = if (iconAtTop) Alignment.Top else Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .padding(top = if (iconAtTop) 2.dp else 0.dp)
                    .size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) { content() }
        }
    }
}

/**
 * One collapsible calendar group rendered as a connected card. The header row is
 * the card's top (tap to expand/collapse): a [leading] source mark (the account
 * app's logo, or a device chip for local), the group [title] and — when
 * [showToggleAll] — a "toggle all" [Switch] that enables or disables every
 * calendar in the group at once.
 *
 * When expanded, the body opens with a differentiated management row
 * ([manageIcon] / [manageLabel] → [onManage]: add a calendar, or open the source
 * app) above the plain calendar rows supplied by [body]; [manageConnectsBelow]
 * is false when no calendar rows follow it, so it rounds off as the card's foot.
 *
 * An active group gets a tinted header; once [headerDisabled] (every calendar in
 * the group switched off) the header drops to a plain, dimmed row so the whole
 * group reads as off — not just its switch.
 */
@Composable
private fun CalendarGroup(
    title: String,
    expanded: Boolean,
    headerDisabled: Boolean,
    leading: @Composable () -> Unit,
    manageIcon: ImageVector,
    manageLabel: String,
    onManage: () -> Unit,
    manageConnectsBelow: Boolean,
    onToggleExpand: () -> Unit,
    showToggleAll: Boolean,
    allEnabled: Boolean,
    toggleAllLabel: String,
    onToggleAll: (Boolean) -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    GroupedRow(
        title = title,
        position = if (expanded) Position.Top else Position.Alone,
        // The account header keeps the standard row colour; the options it reveals
        // sit one tone darker, so the group reads as a header over nested content.
        dimmed = headerDisabled,
        leading = leading,
        trailing = if (showToggleAll) {
            {
                Switch(
                    checked = allEnabled,
                    onCheckedChange = onToggleAll,
                    modifier = Modifier.semantics { contentDescription = toggleAllLabel },
                )
            }
        } else {
            null
        },
        onClick = onToggleExpand,
    )
    AnimatedVisibility(
        visible = expanded,
        enter = calendarExpandEnter(),
        exit = calendarCollapseExit(),
    ) {
        Column {
            GroupedRow(
                title = manageLabel,
                position = if (manageConnectsBelow) Position.Middle else Position.Bottom,
                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                dimmed = headerDisabled,
                leading = { Icon(manageIcon, contentDescription = null) },
                onClick = onManage,
            )
            body()
        }
    }
}

/**
 * The source app's launcher icon (Google Calendar, DAVx5, Nextcloud, …) as a
 * round 40dp chip, so each synced account is recognisable at a glance. We load
 * whatever app owns the account from [PackageManager] rather than bundling brand
 * logos — always accurate, nothing to license. Falls back to a neutral cloud
 * chip when no installed app resolves for the account.
 */
@Composable
private fun SourceLogo(accountType: String) {
    val context = LocalContext.current
    val logo = remember(accountType) { sourceAppLogo(context, accountType) }
    if (logo != null) {
        Image(
            bitmap = logo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
        )
    } else {
        LeadingAvatar(Icons.Default.Cloud)
    }
}

/** The launcher icon of the app backing [accountType], preferring the human-facing app. */
private fun sourceAppLogo(context: Context, accountType: String): ImageBitmap? {
    val pm = context.packageManager
    val candidates = buildList {
        curatedSourcePackage(accountType)?.let { add(it) }
        AccountManager.get(context).authenticatorTypes
            .firstOrNull { it.type.equals(accountType, ignoreCase = true) }
            ?.packageName
            ?.let { add(it) }
    }
    for (pkg in candidates) {
        val bitmap = runCatching { pm.getApplicationIcon(pkg).toBitmap() }.getOrNull()
        if (bitmap != null) return bitmap.asImageBitmap()
    }
    return null
}

/** Neutral circular chip carrying an arbitrary icon (e.g. the local-device mark). */
@Composable
private fun LeadingAvatar(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

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
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
}

/**
 * Pick the app to open for managing a synced calendar's account. The account's
 * own authenticator package (resolved from [AccountManager], no permission
 * needed) handles any sync provider — DAVx5, ICSx5, Nextcloud, … — and a small
 * curated map redirects the few cases where the authenticator isn't the app to
 * open (Google's authenticator is Play Services, but users want the Calendar
 * app). Falls back to the system account settings when nothing launchable is
 * found, so the button always lands somewhere sensible.
 */
private fun sourceAppIntent(context: Context, accountType: String): Intent {
    val pm = context.packageManager
    val candidates = buildList {
        AccountManager.get(context).authenticatorTypes
            .firstOrNull { it.type.equals(accountType, ignoreCase = true) }
            ?.packageName
            ?.let { add(it) }
        curatedSourcePackage(accountType)?.let { add(it) }
    }
    for (pkg in candidates) {
        pm.getLaunchIntentForPackage(pkg)?.let { return it }
    }
    return Intent(Settings.ACTION_SYNC_SETTINGS)
}

/** Preferred app for account types whose authenticator isn't the app to open. */
private fun curatedSourcePackage(accountType: String): String? = when {
    accountType.equals("com.google", ignoreCase = true) -> "com.google.android.calendar"
    else -> null
}

