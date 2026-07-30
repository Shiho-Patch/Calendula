package de.jeanlucmakiola.calendula.ui.calendars

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.CalendarStateLabel
import de.jeanlucmakiola.calendula.domain.hasVisibilitySwitch
import de.jeanlucmakiola.calendula.domain.isNotSynced
import de.jeanlucmakiola.calendula.domain.orderedForManager
import de.jeanlucmakiola.calendula.domain.stateLabels
import de.jeanlucmakiola.calendula.data.calendar.CalendarColorPalette
import de.jeanlucmakiola.calendula.ui.common.AccountKey
import de.jeanlucmakiola.calendula.ui.common.CalendarColorChip
import de.jeanlucmakiola.calendula.ui.common.accountGroupTitle
import de.jeanlucmakiola.calendula.ui.common.groupByAccount
import de.jeanlucmakiola.calendula.ui.common.LocalSoftenColors
import de.jeanlucmakiola.calendula.ui.common.eventFill
import de.jeanlucmakiola.calendula.ui.common.LeadingAvatar
import de.jeanlucmakiola.calendula.ui.common.SourceLogo
import de.jeanlucmakiola.calendula.ui.common.curatedSourcePackage
import de.jeanlucmakiola.floret.components.CollapsingScaffold
import de.jeanlucmakiola.floret.identity.collapseExit
import de.jeanlucmakiola.floret.identity.expandEnter
import de.jeanlucmakiola.floret.identity.predictiveBack
import de.jeanlucmakiola.calendula.ui.common.ColorSwatchRow
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.InlineTextField
import de.jeanlucmakiola.floret.components.Position

/** Sentinel [editorId] meaning "the editor is composing a new calendar". */
private const val NEW_CALENDAR_ID = Long.MIN_VALUE

/**
 * Calendar manager (reached from Settings). Lists the app's own device-only
 * calendars with create / rename / recolor / delete (via a full-screen editor),
 * and lists synced calendars read-only with a per-account "manage in the source
 * app" deep-link — the app never touches a synced calendar's server.
 *
 * Export/import lives in its own Settings entry ([BackupScreen], #69); this
 * screen only points at it, because the two are looked for separately: "which
 * calendars do I have" versus "keep a copy of them". A full-screen destination;
 * [onBack] pops it.
 */
@Composable
fun CalendarsScreen(
    onBack: () -> Unit,
    onOpenBackup: () -> Unit,
    viewModel: CalendarsViewModel = hiltViewModel(),
) {
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()
    val deleteLockedIds by viewModel.deleteLockedCalendarIds.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

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
            deleteLocked = editing != null && editing.id in deleteLockedIds,
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
            error = error,
            onConsumeError = viewModel::consumeError,
            onOpenBackup = onOpenBackup,
            onBack = onBack,
            onAdd = { editorSession++; editorId = NEW_CALENDAR_ID },
            onEdit = { calendar -> editorSession++; editorId = calendar.id },
            onSetVisible = viewModel::setCalendarVisible,
            onSetAccountVisible = viewModel::setAccountVisible,
        )
    }
}

@Composable
private fun CalendarsList(
    local: List<CalendarSource>,
    synced: List<CalendarSource>,
    error: Boolean,
    onConsumeError: () -> Unit,
    onOpenBackup: () -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (CalendarSource) -> Unit,
    onSetVisible: (Long, Boolean) -> Unit,
    onSetAccountVisible: (Collection<Long>, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    // Accounts the user has folded shut; empty = all expanded (keeps every
    // calendar visible by default, the section is collapsible for tidiness).
    var collapsedAccounts by remember { mutableStateOf(emptySet<AccountKey>()) }
    var localExpanded by remember { mutableStateOf(true) }
    val writeErrorText = stringResource(R.string.calendars_write_error)

    LaunchedEffect(error) {
        if (error) {
            snackbarHostState.showSnackbar(writeErrorText)
            onConsumeError()
        }
    }

    CollapsingScaffold(
        title = stringResource(R.string.calendars_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        predictiveBack = true,
    ) {
        // What the per-calendar / per-account switches below actually do.
        HintText(stringResource(R.string.calendars_visibility_hint))

        // Local (device-only) calendars — one collapsible group. The header's
        // "+" adds a calendar; the switch enables/disables them all at once;
        // tapping a calendar row opens its editor.
        val localDisabled = local.isNotEmpty() && local.none { it.isVisibleInSystem }
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
            allEnabled = local.all { it.isVisibleInSystem },
            onToggleAll = { enabled -> onSetAccountVisible(local.map { it.id }, enabled) },
        ) {
            if (local.isEmpty()) {
                HintText(stringResource(R.string.calendars_local_empty))
            } else {
                local.forEachIndexed { index, calendar ->
                    val disabled = !calendar.isVisibleInSystem
                    GroupedRow(
                        title = calendar.displayName,
                        summary = calendarRowSummary(calendar),
                        position = if (index == local.lastIndex) Position.Bottom else Position.Middle,
                        container = MaterialTheme.colorScheme.surfaceContainerHighest,
                        dimmed = disabled,
                        leading = { CalendarColorChip(calendar.color, dimIf(disabled)) },
                        trailing = {
                            EnableSwitch(
                                calendarName = calendar.displayName,
                                enabled = !disabled,
                                onToggle = { enabled -> onSetVisible(calendar.id, enabled) },
                            )
                        },
                        onClick = { onEdit(calendar) },
                    )
                }
            }
        }

        // Backup lives in its own Settings entry now (#69) — this row is the
        // pointer, so someone looking at their local calendars still finds the
        // way to keep a copy of them.
        Spacer(Modifier.height(16.dp))
        GroupedRow(
            title = stringResource(R.string.settings_section_backup),
            summary = stringResource(R.string.settings_backup_subtitle),
            position = Position.Alone,
            leading = { LeadingAvatar(Icons.Default.Backup) },
            trailing = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = onOpenBackup,
        )

        Spacer(Modifier.height(16.dp))

        // Synced calendars — read-only, grouped by account. Each account is a
        // collapsible group whose header opens the source app (icon) and toggles
        // all of its calendars at once (switch).
        SectionHeader(stringResource(R.string.calendars_synced_header))
        HintText(stringResource(R.string.calendars_synced_hint))
        synced
            .groupByAccount()
            .forEach { group ->
                val cals = group.calendars
                val expanded = group.key !in collapsedAccounts
                val accountType = group.accountType
                // A non-syncing calendar has no switch, so it neither counts
                // towards "the whole account is off" nor moves with toggle-all.
                val switchable = cals.filter { it.hasVisibilitySwitch }
                val accountDisabled = switchable.isNotEmpty() &&
                    switchable.none { it.isVisibleInSystem }
                Spacer(Modifier.height(16.dp))
                CalendarGroup(
                    title = accountGroupTitle(group),
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
                            collapsedAccounts + group.key
                        } else {
                            collapsedAccounts - group.key
                        }
                    },
                    showToggleAll = switchable.isNotEmpty(),
                    allEnabled = switchable.all { it.isVisibleInSystem },
                    onToggleAll = { enabled ->
                        onSetAccountVisible(switchable.map { it.id }, enabled)
                    },
                ) {
                    // Calendars you can act on first; the ones this device isn't
                    // syncing sit at the bottom, dimmed and switchless.
                    val ordered = cals.orderedForManager()
                    ordered.forEachIndexed { index, calendar ->
                        val disabled = !calendar.isVisibleInSystem || calendar.isNotSynced
                        GroupedRow(
                            title = calendar.displayName,
                            summary = calendarRowSummary(calendar),
                            position = if (index == ordered.lastIndex) Position.Bottom else Position.Middle,
                            container = MaterialTheme.colorScheme.surfaceContainerHighest,
                            dimmed = disabled,
                            leading = { CalendarColorChip(calendar.color, dimIf(disabled)) },
                            trailing = if (calendar.hasVisibilitySwitch) {
                                {
                                    EnableSwitch(
                                        calendarName = calendar.displayName,
                                        enabled = calendar.isVisibleInSystem,
                                        onToggle = { enabled ->
                                            onSetVisible(calendar.id, enabled)
                                        },
                                    )
                                }
                            } else {
                                null
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
    deleteLocked: Boolean = false,
) {
    var name by rememberSaveable(sessionKey) { mutableStateOf(initialName) }
    var color by rememberSaveable(sessionKey) { mutableStateOf(initialColor) }
    var description by rememberSaveable(sessionKey) { mutableStateOf(initialDescription) }
    var confirmDelete by remember { mutableStateOf(false) }
    val dark = isSystemInDarkTheme()
    val soften = LocalSoftenColors.current

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
                        // Kept in place while the special-dates sync owns this
                        // calendar, rather than hidden: the button is where you
                        // expect it, disabled, with the card below saying why —
                        // and it comes back to life the moment the feature is
                        // off, when the delete would actually stick.
                        IconButton(
                            onClick = { confirmDelete = true },
                            enabled = !deleteLocked,
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.event_detail_delete),
                                tint = if (deleteLocked) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
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
            if (deleteLocked) {
                EditorCard(
                    icon = Icons.Default.Info,
                    iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconAtTop = true,
                ) {
                    Text(
                        text = stringResource(R.string.calendars_managed_delete_locked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            EditorCard(icon = Icons.Default.CalendarMonth, iconTint = eventFill(color, dark, soften)) {
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
 * The row's supporting line: the states that make this calendar behave unlike a
 * plain writable one (#76), then its own description. Text rather than badges —
 * a row can carry several of these at once next to a switch, which is exactly
 * what M3 supporting text composes and a row of static chips doesn't.
 */
@Composable
private fun calendarRowSummary(calendar: CalendarSource): String? {
    val states = calendar.stateLabels().map { label ->
        stringResource(
            when (label) {
                CalendarStateLabel.MANAGED -> R.string.calendars_state_managed
                CalendarStateLabel.READ_ONLY -> R.string.calendars_state_read_only
                CalendarStateLabel.NOT_SYNCED -> R.string.calendars_state_not_synced
            },
        )
    }
    val parts = states + listOfNotNull(calendar.description?.takeIf { it.isNotBlank() })
    return parts.joinToString(" · ").ifEmpty { null }
}

/**
 * The per-row on/off control, writing the system's `Calendars.VISIBLE`: checked
 * = the calendar is shown, unchecked = it drops out of every surface (events,
 * filters, pickers) and the provider stops scheduling its reminders. The flag is
 * device-local — nothing is deleted and nothing is synced anywhere. Carries its
 * own content description so the toggle is self-describing to screen readers
 * even on a dimmed row.
 */
@Composable
private fun EnableSwitch(
    calendarName: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val label = stringResource(R.string.calendars_visibility_a11y, calendarName)
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
        enter = expandEnter(),
        exit = collapseExit(),
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
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
internal fun HintText(text: String) {
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

