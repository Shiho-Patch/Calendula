package de.jeanlucmakiola.calendula.ui.calendars

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.BackupStatus
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.isEventTarget
import de.jeanlucmakiola.calendula.ui.common.CalendarColorChip
import de.jeanlucmakiola.calendula.ui.common.LeadingAvatar
import de.jeanlucmakiola.floret.components.CollapsingScaffold
import de.jeanlucmakiola.floret.components.DialogAmountField
import de.jeanlucmakiola.floret.components.DialogUnitDropdown
import de.jeanlucmakiola.floret.components.FullScreenPicker
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.floret.components.positionOf
import java.time.LocalDate

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
 * Backup & restore (#69). Local calendars aren't synced anywhere, so a `.ics`
 * export is their only safety net — that made it a data-safety feature hiding in
 * the calendar manager, where nobody went looking. It now has its own Settings
 * entry, sharing [CalendarsViewModel] with the manager (both are driven by the
 * same calendar list).
 *
 * A full-screen destination hoisted in `CalendarHost`; [onBack] pops it,
 * [onImport] hands a picked file to the app's normal .ics import flow.
 */
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    onImport: (Uri) -> Unit,
    viewModel: CalendarsViewModel = hiltViewModel(),
) {
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()
    val backupResult by viewModel.backupResult.collectAsStateWithLifecycle()
    val autoBackup by viewModel.autoBackup.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Export covers the user's own local calendars (managed special-dates
    // mirrors don't count — they are rebuilt from contacts). Restore can target
    // any calendar the import picker would offer, so its availability is broader.
    val exportable = calendars.filter { it.isLocal && it.canModifyContents && !it.isManaged }
    val canImport = calendars.any { it.isEventTarget }

    // SAF "create document" target for the backup file. The picked Uri is handed
    // to the VM to stream the .ics into. This launcher exports everything
    // eligible (null); the per-calendar selector owns its own launcher.
    val createBackup = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/calendar"),
    ) { uri -> uri?.let { viewModel.exportBackup(it, null) } }
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
    ) { uri -> uri?.let(viewModel::setAutoBackupFolder) }
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
                viewModel.consumeBackupResult()
            }
            BackupResult.Failure -> {
                snackbarHostState.showSnackbar(backupFailedText)
                viewModel.consumeBackupResult()
            }
            null -> Unit
        }
    }

    CollapsingScaffold(
        title = stringResource(R.string.settings_section_backup),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        predictiveBack = true,
    ) {
        HintText(stringResource(R.string.calendars_backup_hint))

        if (exportable.isNotEmpty()) {
            // One connected card: the one-time export on top, restore under it,
            // then automatic backup (and its folder/interval rows when on).
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
                onClick = { runCatching { openBackup.launch(RESTORE_MIME_TYPES) } },
            )
            GroupedRow(
                title = stringResource(R.string.calendars_auto_backup),
                summary = stringResource(R.string.calendars_auto_backup_hint),
                position = if (autoBackup.enabled) Position.Middle else Position.Bottom,
                leading = { LeadingAvatar(Icons.Default.Schedule) },
                trailing = {
                    Switch(checked = autoBackup.enabled, onCheckedChange = viewModel::setAutoBackupEnabled)
                },
                onClick = { viewModel.setAutoBackupEnabled(!autoBackup.enabled) },
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
            SectionHeader(stringResource(R.string.calendars_restore_header))
            HintText(stringResource(R.string.calendars_restore_hint))
            GroupedRow(
                title = stringResource(R.string.calendars_restore_action),
                position = Position.Alone,
                leading = { LeadingAvatar(Icons.Default.FileUpload) },
                onClick = { runCatching { openBackup.launch(RESTORE_MIME_TYPES) } },
            )
        }
    }

    if (showExportPicker) {
        ExportCalendarPicker(
            calendars = exportable,
            onExport = viewModel::exportBackup,
            onDismiss = { showExportPicker = false },
        )
    }
    if (showInterval) {
        BackupIntervalDialog(
            currentMinutes = autoBackup.intervalMinutes,
            onConfirm = viewModel::setAutoBackupIntervalMinutes,
            onDismiss = { showInterval = false },
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
    onExport: (Uri, Set<Long>?) -> Unit,
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
