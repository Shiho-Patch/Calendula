package de.jeanlucmakiola.calendula.ui.calendars

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.ui.common.InlineTextField
import de.jeanlucmakiola.calendula.ui.common.pastelize

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
            error = error,
            onConsumeError = viewModel::consumeError,
            onBack = onBack,
            onAdd = { editorSession++; editorId = NEW_CALENDAR_ID },
            onEdit = { calendar -> editorSession++; editorId = calendar.id },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarsList(
    local: List<CalendarSource>,
    synced: List<CalendarSource>,
    error: Boolean,
    onConsumeError: () -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (CalendarSource) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val writeErrorText = stringResource(R.string.calendars_write_error)
    val dark = isSystemInDarkTheme()

    BackHandler(onBack = onBack)
    LaunchedEffect(error) {
        if (error) {
            snackbarHostState.showSnackbar(writeErrorText)
            onConsumeError()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendars_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.calendars_local_header))
            if (local.isEmpty()) {
                HintText(stringResource(R.string.calendars_local_empty))
            } else {
                local.forEach { calendar ->
                    CalendarRow(
                        name = calendar.displayName,
                        color = calendar.color,
                        dark = dark,
                        subtitle = calendar.description,
                        onClick = { onEdit(calendar) },
                        trailing = {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.calendars_edit_title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                    )
                }
            }
            FilledTonalButton(
                onClick = onAdd,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.calendars_add))
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader(stringResource(R.string.calendars_synced_header))
            HintText(stringResource(R.string.calendars_synced_hint))
            synced
                .groupBy { it.accountName.ifBlank { it.accountType } }
                .forEach { (account, cals) ->
                    SyncedAccountGroup(
                        account = account,
                        accountType = cals.first().accountType,
                        calendars = cals,
                        dark = dark,
                    )
                }
            AddAccountButton()
            Spacer(Modifier.height(24.dp))
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

    BackHandler(onBack = onClose)

    Scaffold(
        modifier = Modifier
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
                ColorPalette(selected = color, onSelect = { color = it }, dark = dark)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPalette(selected: Int, onSelect: (Int) -> Unit, dark: Boolean) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CALENDAR_COLOR_PALETTE.forEach { argb ->
            val isSelected = argb == selected
            // Show the pastel the calendar will actually render as, not the raw hue.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(pastelize(argb, dark))
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(argb) },
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncedAccountGroup(
    account: String,
    accountType: String,
    calendars: List<CalendarSource>,
    dark: Boolean,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = account,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = {
            runCatching { context.startActivity(sourceAppIntent(context, accountType)) }
        }) {
            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.calendars_manage_in_app))
        }
    }
    calendars.forEach { calendar ->
        CalendarRow(name = calendar.displayName, color = calendar.color, dark = dark)
    }
}

@Composable
private fun AddAccountButton() {
    val context = LocalContext.current
    FilledTonalButton(
        onClick = {
            runCatching { context.startActivity(Intent(Settings.ACTION_ADD_ACCOUNT)) }
        },
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.calendars_add_account))
    }
}

@Composable
private fun CalendarRow(
    name: String,
    color: Int,
    dark: Boolean,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(pastelize(color, dark)),
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
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

/** Google-Calendar-style palette; values are ARGB ints for `Calendars.CALENDAR_COLOR`. */
internal val CALENDAR_COLOR_PALETTE: List<Int> = listOf(
    0xFFD50000, // red
    0xFFE67C00, // orange
    0xFFF6BF26, // amber
    0xFF33B679, // green
    0xFF0B8043, // dark green
    0xFF039BE5, // blue
    0xFF3F51B5, // indigo
    0xFF8E24AA, // purple
    0xFF616161, // graphite
).map { it.toInt() }
