package de.jeanlucmakiola.calendula.ui.calendars

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.BackupStatus
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.data.calendar.CalendarColorPalette
import de.jeanlucmakiola.calendula.ui.common.CalendarColorChip
import de.jeanlucmakiola.calendula.ui.common.FullScreenPicker
import de.jeanlucmakiola.calendula.ui.common.positionOf
import de.jeanlucmakiola.calendula.ui.common.LeadingAvatar
import de.jeanlucmakiola.calendula.ui.common.SourceLogo
import de.jeanlucmakiola.calendula.ui.common.curatedSourcePackage
import de.jeanlucmakiola.calendula.ui.common.DialogAmountField
import de.jeanlucmakiola.calendula.ui.common.DialogUnitDropdown
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

// SAF mime filter for the restore picker. `.ics` files reach us under several
// mimes depending on the source app (our own export uses text/calendar; others
// hand them out as octet-stream or text/plain), so accept the common set rather
// than hide valid backups behind an over-tight filter.
private val RESTORE_MIME_TYPES = arrayOf(
    "text/calendar",
    "application/octet-stream",
    "text/plain",
)

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
    onImport: (android.net.Uri) -> Unit,
    viewModel: CalendarsViewModel = hiltViewModel(),
) {
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()
    val disabledIds by viewModel.disabledCalendarIds.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val backupResult by viewModel.backupResult.collectAsStateWithLifecycle()
    val autoBackup by viewModel.autoBackup.collectAsStateWithLifecycle()

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
            initialColor = editing?.color ?: CalendarColorPalette.all.first(),
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
            onImport = onImport,
            onConsumeBackupResult = viewModel::consumeBackupResult,
            autoBackup = autoBackup,
            onSetAutoBackupEnabled = viewModel::setAutoBackupEnabled,
            onSetAutoBackupInterval = viewModel::setAutoBackupIntervalMinutes,
            onSetAutoBackupFolder = viewModel::setAutoBackupFolder,
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
    onExportBackup: (android.net.Uri, Set<Long>?) -> Unit,
    onImport: (android.net.Uri) -> Unit,
    onConsumeBackupResult: () -> Unit,
    autoBackup: AutoBackupUiState,
    onSetAutoBackupEnabled: (Boolean) -> Unit,
    onSetAutoBackupInterval: (Long) -> Unit,
    onSetAutoBackupFolder: (android.net.Uri) -> Unit,
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
    // to the VM to stream the .ics into. This launcher exports everything
    // eligible (null); the per-calendar selector owns its own launcher.
    val createBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/calendar"),
    ) { uri -> uri?.let { onExportBackup(it, null) } }
    var showExportPicker by rememberSaveable { mutableStateOf(false) }

    // SAF "open document" picker for restoring events from a .ics file. The
    // picked Uri is handed up to the host, which runs it through the same import
    // flow as an externally opened .ics (parse, dedup by UID, target picker).
    val openBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImport) }

    // SAF folder picker for the automatic-backup destination; the VM persists the
    // write grant so background runs can keep writing to it.
    val pickFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(onSetAutoBackupFolder) }
    var showInterval by remember { mutableStateOf(false) }

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
            bodyHasRows = local.isNotEmpty(),
            headerDisabled = localDisabled,
            leading = { Box(dimIf(localDisabled)) { LeadingAvatar(Icons.Default.PhoneAndroid) } },
            manageIcon = Icons.Default.Add,
            manageLabel = stringResource(R.string.calendars_add),
            onManage = onAdd,
            onToggleExpand = { localExpanded = !localExpanded },
            showToggleAll = local.isNotEmpty(),
            allEnabled = local.none { it.id in disabledIds },
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
        // safety net. Offered only when there is something exportable: the user's
        // own local calendars (managed special-dates mirrors don't count).
        val exportable = local.filter { it.canModifyContents && !it.isManaged }
        // Restore/import can target any writable, non-managed calendar (local or
        // synced), so its availability is broader than export's.
        val canImport = (local + synced).any { it.canModifyContents && !it.isManaged }
        if (exportable.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionHeader(stringResource(R.string.calendars_backup_header))
            HintText(stringResource(R.string.calendars_backup_hint))
            // One connected card: the one-time export on top, then automatic
            // backup (and its folder/interval rows when on).
            GroupedRow(
                title = stringResource(R.string.calendars_backup_action),
                position = Position.Top,
                leading = { LeadingAvatar(Icons.Default.FileDownload) },
                onClick = {
                    // With more than one exportable calendar, let the user choose
                    // which to include; a single one exports straight away.
                    if (exportable.size == 1) {
                        runCatching { createBackup.launch("calendula-backup-${LocalDate.now()}.ics") }
                    } else {
                        showExportPicker = true
                    }
                },
            )
            GroupedRow(
                title = stringResource(R.string.calendars_restore_action),
                summary = stringResource(R.string.calendars_restore_hint),
                position = Position.Middle,
                leading = { LeadingAvatar(Icons.Default.FileUpload) },
                onClick = {
                    runCatching { openBackup.launch(RESTORE_MIME_TYPES) }
                },
            )
            GroupedRow(
                title = stringResource(R.string.calendars_auto_backup),
                summary = stringResource(R.string.calendars_auto_backup_hint),
                position = if (autoBackup.enabled) Position.Middle else Position.Bottom,
                leading = { LeadingAvatar(Icons.Default.Schedule) },
                trailing = {
                    Switch(checked = autoBackup.enabled, onCheckedChange = onSetAutoBackupEnabled)
                },
                onClick = { onSetAutoBackupEnabled(!autoBackup.enabled) },
            )
            if (autoBackup.enabled) {
                GroupedRow(
                    title = stringResource(R.string.calendars_auto_backup_folder),
                    summary = rememberFolderName(autoBackup.folderUri)
                        ?: stringResource(R.string.calendars_auto_backup_folder_unset),
                    position = Position.Middle,
                    onClick = { runCatching { pickFolder.launch(null) } },
                )
                GroupedRow(
                    title = stringResource(R.string.calendars_auto_backup_interval),
                    summary = backupIntervalLabel(autoBackup.intervalMinutes),
                    position = Position.Bottom,
                    onClick = { showInterval = true },
                )
                HintText(backupStatusText(autoBackup.status))
            }
        } else if (canImport) {
            // Nothing to back up (no writable local calendar), but events can
            // still be restored into a writable calendar — offer restore on its
            // own so it isn't hidden behind export eligibility.
            Spacer(Modifier.height(16.dp))
            SectionHeader(stringResource(R.string.calendars_restore_header))
            HintText(stringResource(R.string.calendars_restore_hint))
            GroupedRow(
                title = stringResource(R.string.calendars_restore_action),
                position = Position.Alone,
                leading = { LeadingAvatar(Icons.Default.FileUpload) },
                onClick = { runCatching { openBackup.launch(RESTORE_MIME_TYPES) } },
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
                    bodyHasRows = true,
                    headerDisabled = accountDisabled,
                    leading = { Box(dimIf(accountDisabled)) { SourceLogo(accountType) } },
                    manageIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    manageLabel = stringResource(R.string.calendars_manage_in_app),
                    onManage = {
                        runCatching { context.startActivity(sourceAppIntent(context, accountType)) }
                    },
                    onToggleExpand = {
                        collapsedAccounts = if (expanded) {
                            collapsedAccounts + account
                        } else {
                            collapsedAccounts - account
                        }
                    },
                    showToggleAll = true,
                    allEnabled = cals.none { it.id in disabledIds },
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

    if (showInterval) {
        BackupIntervalDialog(
            currentMinutes = autoBackup.intervalMinutes,
            onConfirm = onSetAutoBackupInterval,
            onDismiss = { showInterval = false },
        )
    }

    if (showExportPicker) {
        ExportCalendarPicker(
            calendars = local.filter { it.canModifyContents && !it.isManaged },
            onExport = onExportBackup,
            onDismiss = { showExportPicker = false },
        )
    }
}

/**
 * Choose which local calendars to include in a one-time `.ics` export. Defaults
 * to all selected; the Export action opens the SAF save dialog and hands back
 * the picked file with the chosen calendar ids.
 */
@Composable
private fun ExportCalendarPicker(
    calendars: List<CalendarSource>,
    onExport: (android.net.Uri, Set<Long>?) -> Unit,
    onDismiss: () -> Unit,
) {
    // Seed once with everything selected and hold it across recomposition and
    // rotation. NOT keyed on [calendars]: the list is observer-driven, so keying
    // it would silently reset the user's de-selections whenever the provider
    // re-emits (a background sync, a recolor). Ids that later vanish are harmless
    // — the data layer intersects the chosen set with the eligible calendars.
    var selected by rememberSaveable(
        stateSaver = listSaver(
            save = { it.toList() },
            restore = { it.toSet() },
        ),
    ) {
        mutableStateOf(calendars.map { it.id }.toSet())
    }
    val createBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/calendar"),
    ) { uri ->
        if (uri != null) {
            onExport(uri, selected)
            onDismiss()
        }
    }

    FullScreenPicker(
        title = stringResource(R.string.calendars_export_title),
        onDismiss = onDismiss,
    ) {
        HintText(stringResource(R.string.calendars_export_hint))
        calendars.forEachIndexed { index, calendar ->
            val isSelected = calendar.id in selected
            GroupedRow(
                title = calendar.displayName,
                summary = calendar.description,
                position = positionOf(index, calendars.size),
                leading = { CalendarColorChip(calendar.color) },
                trailing = {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { checked ->
                            selected = if (checked) selected + calendar.id else selected - calendar.id
                        },
                    )
                },
                onClick = {
                    selected = if (isSelected) selected - calendar.id else selected + calendar.id
                },
            )
        }
        Button(
            onClick = {
                runCatching { createBackup.launch("calendula-backup-${LocalDate.now()}.ics") }
            },
            enabled = selected.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            Text(stringResource(R.string.calendars_export_action))
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
                    colors = CalendarColorPalette.all,
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
 * app's logo, or a device chip for local), the group [title] and a trailing
 * overflow (⋮) menu holding the account-level actions — enable/disable every
 * calendar at once (when [showToggleAll]) and the manage/add action
 * ([manageIcon] / [manageLabel] → [onManage]). The group's calendars render as
 * the rows below via [body].
 *
 * The header keeps the standard row colour; the calendars it reveals sit one tone
 * darker, so the group reads as a header over nested content. Once [headerDisabled]
 * (every calendar switched off) the header fades to the disabled emphasis so the
 * whole group reads as off — not just via the menu. [bodyHasRows] is false when no
 * calendar rows follow (e.g. an empty local group), so the header stays a
 * standalone card rather than a top edge with nothing beneath it.
 */
@Composable
private fun CalendarGroup(
    title: String,
    expanded: Boolean,
    bodyHasRows: Boolean,
    headerDisabled: Boolean,
    leading: @Composable () -> Unit,
    manageIcon: ImageVector,
    manageLabel: String,
    onManage: () -> Unit,
    onToggleExpand: () -> Unit,
    showToggleAll: Boolean,
    allEnabled: Boolean,
    onToggleAll: (Boolean) -> Unit,
    body: @Composable ColumnScope.() -> Unit,
) {
    GroupedRow(
        title = title,
        position = if (expanded && bodyHasRows) Position.Top else Position.Alone,
        dimmed = headerDisabled,
        leading = leading,
        trailing = {
            CalendarGroupMenu(
                title = title,
                showToggleAll = showToggleAll,
                allEnabled = allEnabled,
                onToggleAll = onToggleAll,
                manageIcon = manageIcon,
                manageLabel = manageLabel,
                onManage = onManage,
            )
        },
        onClick = onToggleExpand,
    )
    AnimatedVisibility(
        visible = expanded,
        enter = calendarExpandEnter(),
        exit = calendarCollapseExit(),
    ) {
        Column(content = body)
    }
}

/**
 * The account header's overflow (⋮) menu: the two account-level actions that
 * don't fit on one line — "Enable/Disable all" (when [showToggleAll]) and the
 * manage/add action. A rounded, tonal dropdown matching the app's surfaces, with
 * the two actions divided for clear separation.
 */
@Composable
private fun CalendarGroupMenu(
    title: String,
    showToggleAll: Boolean,
    allEnabled: Boolean,
    onToggleAll: (Boolean) -> Unit,
    manageIcon: ImageVector,
    manageLabel: String,
    onManage: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.calendars_account_menu_a11y, title),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(20.dp),
            // A distinct tone + a lifted shadow so the menu reads as floating
            // above the cards (which sit at surfaceContainerHigh) rather than
            // blending into them.
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
        ) {
            if (showToggleAll) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (allEnabled) R.string.calendars_disable_all
                                else R.string.calendars_enable_all,
                            ),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (allEnabled) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        open = false
                        onToggleAll(!allEnabled)
                    },
                )
                HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
            DropdownMenuItem(
                text = { Text(manageLabel) },
                leadingIcon = { Icon(manageIcon, contentDescription = null) },
                onClick = {
                    open = false
                    onManage()
                },
            )
        }
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

/** Readable name of the persisted backup folder, resolved from its tree Uri. */
@Composable
private fun rememberFolderName(uriString: String?): String? {
    val context = LocalContext.current
    return remember(uriString) {
        uriString?.let {
            runCatching { DocumentFile.fromTreeUri(context, it.toUri())?.name }.getOrNull()
        }
    }
}

/** "Every 30 minutes" / "Every 2 hours" / "Every day" — the interval in its largest whole unit. */
@Composable
private fun backupIntervalLabel(minutes: Long): String {
    val duration = when {
        minutes % MINUTES_PER_WEEK == 0L ->
            pluralStringResource(R.plurals.duration_weeks, (minutes / MINUTES_PER_WEEK).toInt(), (minutes / MINUTES_PER_WEEK).toInt())
        minutes % MINUTES_PER_DAY == 0L ->
            pluralStringResource(R.plurals.duration_days, (minutes / MINUTES_PER_DAY).toInt(), (minutes / MINUTES_PER_DAY).toInt())
        minutes % 60L == 0L ->
            pluralStringResource(R.plurals.duration_hours, (minutes / 60L).toInt(), (minutes / 60L).toInt())
        else ->
            pluralStringResource(R.plurals.duration_minutes, minutes.toInt(), minutes.toInt())
    }
    return stringResource(R.string.calendars_auto_backup_every, duration)
}

/** "Last backup: 5 minutes ago" / "… failed" / "No automatic backup yet". */
@Composable
private fun backupStatusText(status: BackupStatus): String {
    if (status.lastRun == 0L) return stringResource(R.string.calendars_auto_backup_status_never)
    val relative = DateUtils.getRelativeTimeSpanString(
        status.lastRun, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    return if (status.lastSuccess) {
        stringResource(R.string.calendars_auto_backup_status_ok, relative)
    } else {
        stringResource(R.string.calendars_auto_backup_status_failed, relative)
    }
}

/** Amount + unit picker for the backup interval (floored at 30 minutes). */
@Composable
private fun BackupIntervalDialog(
    currentMinutes: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    // minutes-per-unit for each entry; pick the largest unit the current value divides into.
    val unitMinutes = remember { listOf(1L, 60L, MINUTES_PER_DAY, MINUTES_PER_WEEK) }
    val units = stringArrayResource(R.array.backup_interval_units).toList()
    val initialUnit = unitMinutes.indexOfLast { currentMinutes % it == 0L }.coerceAtLeast(0)
    var amount by rememberSaveable { mutableStateOf((currentMinutes / unitMinutes[initialUnit]).toString()) }
    var unitIndex by rememberSaveable { mutableStateOf(initialUnit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.calendars_auto_backup_interval)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DialogAmountField(value = amount, onValueChange = { amount = it }, placeholder = "1")
                    Spacer(Modifier.width(12.dp))
                    DialogUnitDropdown(label = units[unitIndex], entries = units) { unitIndex = it }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.calendars_auto_backup_interval_min),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = amount.toLongOrNull()?.coerceAtLeast(1L) ?: 1L
                onConfirm((value * unitMinutes[unitIndex]).coerceAtLeast(SettingsPrefs.MIN_BACKUP_INTERVAL))
                onDismiss()
            }) { Text(stringResource(R.string.reminder_custom_set)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

private const val MINUTES_PER_DAY = 1_440L
private const val MINUTES_PER_WEEK = 10_080L

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

