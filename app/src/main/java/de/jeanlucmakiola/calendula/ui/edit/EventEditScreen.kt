package de.jeanlucmakiola.calendula.ui.edit

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.AccessLevel
import de.jeanlucmakiola.calendula.domain.Availability
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventAttendee
import de.jeanlucmakiola.calendula.domain.EventColorOption
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventFormField
import de.jeanlucmakiola.calendula.domain.zoneDescriptor
import de.jeanlucmakiola.calendula.domain.timeZoneOptionOf
import de.jeanlucmakiola.calendula.domain.timesIn
import de.jeanlucmakiola.calendula.domain.EventFormProblem
import de.jeanlucmakiola.calendula.domain.RecurrenceEnd
import de.jeanlucmakiola.calendula.domain.RecurrenceFreq
import de.jeanlucmakiola.calendula.domain.RecurringWriteScope
import de.jeanlucmakiola.calendula.domain.SimpleRecurrence
import de.jeanlucmakiola.calendula.domain.parseSimpleRecurrence
import de.jeanlucmakiola.calendula.domain.toRRule
import de.jeanlucmakiola.floret.identity.animateContentSizeMotion
import de.jeanlucmakiola.floret.identity.collapseExit
import de.jeanlucmakiola.floret.identity.expandEnter
import de.jeanlucmakiola.floret.identity.predictiveBack
import de.jeanlucmakiola.calendula.data.calendar.CalendarColorPalette
import de.jeanlucmakiola.calendula.ui.common.CalendarPickerGroups
import de.jeanlucmakiola.calendula.ui.common.CalendarDatePickerDialog
import de.jeanlucmakiola.calendula.ui.common.ColorSwatchRow
import de.jeanlucmakiola.calendula.ui.common.TimeZonePickerDialog
import de.jeanlucmakiola.calendula.ui.common.icuTimeZoneRegion
import de.jeanlucmakiola.calendula.ui.common.LocalSoftenColors
import de.jeanlucmakiola.calendula.ui.common.eventFill
import de.jeanlucmakiola.floret.components.DialogAmountField
import de.jeanlucmakiola.floret.components.DialogUnitDropdown
import de.jeanlucmakiola.calendula.ui.common.eventFormFieldIcon
import de.jeanlucmakiola.calendula.ui.common.eventFormFieldLabel
import de.jeanlucmakiola.floret.components.FullScreenPicker
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.calendula.ui.common.MILLIS_PER_DAY
import de.jeanlucmakiola.floret.components.InlineTextField
import de.jeanlucmakiola.floret.components.OptionCard
import de.jeanlucmakiola.floret.components.OptionPicker
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.floret.components.positionOf
import de.jeanlucmakiola.calendula.ui.common.REMINDER_PRESETS
import de.jeanlucmakiola.calendula.ui.common.TimePickerAlert
import de.jeanlucmakiola.floret.reminders.ReminderUnit
import de.jeanlucmakiola.floret.locale.currentLocale
import de.jeanlucmakiola.calendula.ui.common.LocalUse24HourFormat
import de.jeanlucmakiola.calendula.ui.common.timeOfDayFormatter
import de.jeanlucmakiola.calendula.ui.common.reminderLeadTimeLabel
import de.jeanlucmakiola.calendula.ui.common.reminderUnitLabel
import de.jeanlucmakiola.calendula.ui.common.recurrenceText
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toJavaDayOfWeek
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinDayOfWeek
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toLocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.time.Clock

/**
 * Full-screen event form: create (v1.2, from the FAB's anchor date) and edit
 * (v1.3, [editKey] = eventId + the tapped occurrence's begin/end millis).
 * Save validates and writes via the repository — a dirty recurring event
 * first asks how far the change reaches (this occurrence / this and
 * following / whole series) — then closes. The calendar picker offers only
 * writable calendars; when editing, the calendar is fixed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    initialDateIso: String?,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    editKey: LongArray? = null,
    initialStartMinutes: Int? = null,
    initialForm: EventForm? = null,
    initialFormSource: ImportSource = ImportSource.File,
    viewModel: EventEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(initialDateIso, editKey, initialForm) {
        when {
            // A prefilled open: a single-event .ics for review, or an external
            // ACTION_INSERT intent. The source drives how reminders are seeded.
            initialForm != null -> viewModel.openImported(initialForm, initialFormSource)
            editKey != null -> viewModel.openForEdit(
                eventId = editKey[0],
                beginMillis = editKey[1],
                endMillis = editKey[2],
            )
            else -> {
                val date = initialDateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                viewModel.openNew(date, initialStartMinutes)
            }
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadFailed by viewModel.loadFailed.collectAsStateWithLifecycle()
    val importReminderPrompt by viewModel.importReminderPrompt.collectAsStateWithLifecycle()

    // The form is intentionally forgotten on every close (cancel or save) so
    // the next open starts clean; it survives rotation because openNew /
    // openForEdit no-op while a form is set.
    val close = {
        viewModel.reset()
        onClose()
    }
    // The event vanished between the detail screen and the edit tap — fall
    // back to the detail screen, which shows its own failure state.
    LaunchedEffect(loadFailed) {
        if (loadFailed) close()
    }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Contextual WRITE_CALENDAR upgrade for v1.0 installs, like delete.
    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.save()
    }
    val onSaveClick = {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.save()
        } else {
            writePermissionLauncher.launch(Manifest.permission.WRITE_CALENDAR)
        }
    }

    val saveFailedMessage = stringResource(R.string.event_edit_save_failed)
    val writeDeniedMessage = stringResource(R.string.event_edit_write_denied)
    LaunchedEffect(state?.saveState) {
        when (state?.saveState) {
            SaveUiState.Saved -> {
                viewModel.reset()
                onSaved()
            }
            SaveUiState.Failed -> {
                viewModel.consumeSaveResult()
                snackbarHostState.showSnackbar(saveFailedMessage)
            }
            SaveUiState.NeedsPermission -> {
                viewModel.consumeSaveResult()
                snackbarHostState.showSnackbar(writeDeniedMessage)
            }
            // AwaitingScope/AwaitingConflict/Gone render as dialogs below.
            else -> Unit
        }
    }

    Scaffold(
        modifier = Modifier.predictiveBack(onBack = close),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                // No title — the form's own headline carries the screen,
                // matching the detail screen's bare top bar.
                title = {},
                navigationIcon = {
                    IconButton(onClick = close) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.event_edit_close),
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = onSaveClick,
                        enabled = state != null && state?.saveState != SaveUiState.Saving,
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
        state?.let { s ->
            EventEditContent(
                state = s,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        }
    }

    if (state?.saveState == SaveUiState.AwaitingScope) {
        SaveScopeDialog(
            recurrenceChanged = state?.recurrenceChanged == true,
            onSelect = viewModel::saveWithScope,
            onDismiss = viewModel::consumeSaveResult,
        )
    }

    // The event changed externally (sync) while the form was open (v2.0).
    if (state?.saveState is SaveUiState.AwaitingConflict) {
        SaveConflictDialog(
            onOverwrite = viewModel::saveOverwriting,
            onDiscard = close,
            onDismiss = viewModel::consumeSaveResult,
        )
    }

    // ...or was deleted underneath us — nothing left to save onto. Closing
    // through [onSaved] also pops the detail screen, whose occurrence is gone.
    if (state?.saveState == SaveUiState.Gone) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.event_edit_gone_title)) },
            text = { Text(stringResource(R.string.event_edit_gone_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reset()
                    onSaved()
                }) { Text(stringResource(R.string.dialog_ok)) }
            },
        )
    }

    // A .ics import respects the file's reminders, but offers to swap in the
    // configured default rather than silently deciding for the user (#49).
    importReminderPrompt?.let { prompt ->
        ImportReminderPromptDialog(
            currentReminderCount = prompt.currentReminderCount,
            onApply = viewModel::applyImportedReminderDefault,
            onKeep = viewModel::dismissImportedReminderPrompt,
        )
    }
}

/**
 * Offer to apply the settings default reminder to an event opened from a `.ics`
 * file. The file's own reminders are kept unless the user accepts. A plain
 * two-choice confirmation, so an [AlertDialog] (not a full-screen picker).
 */
@Composable
private fun ImportReminderPromptDialog(
    currentReminderCount: Int,
    onApply: () -> Unit,
    onKeep: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(R.string.import_reminder_prompt_title)) },
        text = {
            Text(
                if (currentReminderCount == 0) {
                    stringResource(R.string.import_reminder_prompt_body_none)
                } else {
                    pluralStringResource(
                        R.plurals.import_reminder_prompt_body_existing,
                        currentReminderCount,
                        currentReminderCount,
                    )
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onApply) {
                Text(stringResource(R.string.import_reminder_prompt_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onKeep) {
                Text(stringResource(R.string.import_reminder_prompt_keep))
            }
        },
    )
}

/**
 * Overwrite-or-discard choice when the event changed underneath an open
 * form (no locking; detected by re-reading at save time). "Overwrite" still
 * only writes the fields the user edited — external changes to untouched
 * fields survive either way. Cancelling returns to the form.
 */
@Composable
private fun SaveConflictDialog(
    onOverwrite: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    FullScreenPicker(
        title = stringResource(R.string.event_edit_conflict_title),
        onDismiss = onDismiss,
    ) {
        Text(
            text = stringResource(R.string.event_edit_conflict_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        GroupedRow(
            title = stringResource(R.string.event_edit_conflict_overwrite),
            summary = stringResource(R.string.event_edit_conflict_overwrite_hint),
            position = positionOf(0, 2),
            onClick = onOverwrite,
        )
        GroupedRow(
            title = stringResource(R.string.event_edit_conflict_discard),
            summary = stringResource(R.string.event_edit_conflict_discard_hint),
            position = positionOf(1, 2),
            onClick = onDiscard,
        )
    }
}

/**
 * Scope choice when saving a dirty recurring event: one tap writes just the
 * tapped occurrence, it and everything after (series split), or the whole
 * series. A changed recurrence rule rules out the single occurrence — an
 * exception row can't carry its own rule.
 */
@Composable
private fun SaveScopeDialog(
    recurrenceChanged: Boolean,
    onSelect: (RecurringWriteScope) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.event_edit_recurring_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!recurrenceChanged) {
                    OptionCard(
                        label = stringResource(R.string.event_delete_option_occurrence),
                        onClick = { onSelect(RecurringWriteScope.ThisEvent) },
                    )
                }
                OptionCard(
                    label = stringResource(R.string.event_delete_option_following),
                    onClick = { onSelect(RecurringWriteScope.ThisAndFollowing) },
                )
                OptionCard(
                    label = stringResource(R.string.event_delete_option_series),
                    onClick = { onSelect(RecurringWriteScope.AllEvents) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

private enum class PickerTarget { StartDate, StartTime, EndDate, EndTime }

/**
 * Wrapper for an optional form section: fields added via "more fields"
 * spring open with the expressive spatial spec instead of popping in.
 * (Initially-visible sections render without animating.)
 */
@Composable
private fun OptionalFormSection(
    visible: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandEnter(),
        exit = collapseExit(),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditContent(
    state: EventEditUiState,
    viewModel: EventEditViewModel,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    val locale = currentLocale()
    val dark = isSystemInDarkTheme()
    // The title, date and recurrence are managed by the special-dates sync, so
    // they're locked here; everything else (reminders, location, notes) is the
    // user's to edit.
    val locked = state.isManaged
    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    var showCalendarPicker by rememberSaveable { mutableStateOf(false) }
    var showReminderPicker by rememberSaveable { mutableStateOf(false) }
    var showRecurrencePicker by rememberSaveable { mutableStateOf(false) }
    var showVisibilityPicker by rememberSaveable { mutableStateOf(false) }
    var showColorPicker by rememberSaveable { mutableStateOf(false) }
    var showTimezonePicker by rememberSaveable { mutableStateOf(false) }
    var showFieldPicker by rememberSaveable { mutableStateOf(false) }

    // Pick a guest from contacts: a one-shot system picker returning the chosen
    // email row (email + display name). No READ_CONTACTS — the result Intent
    // grants temporary read access, so Calendula stays no-network/no-permission.
    val context = LocalContext.current
    val pickContact = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                readContactEmail(context, uri)?.let { (email, name) ->
                    viewModel.addAttendee(email, name)
                }
            }
        }
    }

    // Pick a postal address from contacts: the same one-shot picker, scoped to
    // address rows, dropping the chosen contact's formatted address into the
    // location field. Same no-permission guarantee as the guest picker above.
    val pickContactAddress = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                readContactAddress(context, uri)?.let { address ->
                    viewModel.setLocation(address)
                }
            }
        }
    }

    val selectedCalendar = state.calendars.firstOrNull { it.id == form.calendarId }
    // The accent ties the form to the detail screen's design language: the
    // bar under the title takes the target calendar's colour.
    val soften = LocalSoftenColors.current
    val accent = selectedCalendar?.let { eventFill(it.color, dark, soften) }
        ?: MaterialTheme.colorScheme.primary
    val gap = 12.dp

    // Issue #10: on a fresh create form (setting on, nothing typed yet) focus the
    // title and raise the keyboard so the user can type straight away. Keyed on
    // Unit so it runs once per open — not on every keystroke — and guarded so
    // editing or an imported/prefilled form never grabs focus.
    val titleFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (state.autofocusTitle && !state.isEditing && form.title.isBlank()) {
            runCatching { titleFocusRequester.requestFocus() }
        }
    }

    // Per-event colour applicability for the resolved calendar:
    //  - palette calendars (Google, …) and local calendars always support it;
    //  - synced calendars with no palette only when the user opted in, and even
    //    then the colour may not survive the calendar's next sync (the warning).
    val isLocalCalendar = selectedCalendar?.isLocal == true
    val colorSupported = state.colorPalette.isNotEmpty() || isLocalCalendar ||
        state.allowColorOnUnsupportedCalendars
    val colorSyncRisk = state.colorPalette.isEmpty() && !isLocalCalendar &&
        state.allowColorOnUnsupportedCalendars

    Column(
        modifier = modifier
            // Shrink the scroll viewport by the keyboard instead of letting
            // the window pan — the title stays anchored and the focused
            // field scrolls into view above the IME.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 40.dp),
    ) {
        // Title: borderless headline, mirroring the detail screen's title +
        // accent bar instead of a boxed Material text field. Multi-line so long
        // titles wrap instead of scrolling off one line (#33); newlines are
        // stripped in setTitle, so this stays a single logical line — the Enter
        // key just has no effect.
        InlineField(
            value = form.title,
            onValueChange = viewModel::setTitle,
            placeholder = stringResource(R.string.event_edit_title_hint),
            textStyle = MaterialTheme.typography.headlineMedium
                .copy(fontWeight = FontWeight.SemiBold),
            singleLine = false,
            enabled = !locked,
            modifier = Modifier
                .fillMaxWidth()
                // Ease the height change as the title wraps to another line
                // instead of letting the accent bar + cards below jump.
                .animateContentSizeMotion()
                .padding(vertical = 4.dp)
                .focusRequester(titleFocusRequester),
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(3.dp)
                .background(accent, RoundedCornerShape(2.dp)),
        )
        if (locked) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    R.string.event_edit_managed_hint,
                    selectedCalendar?.displayName.orEmpty(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))

        // "When" card: the icon centres on the all-day row (header); the
        // start/end rows continue below in the same text column.
        EditCard(
            icon = Icons.Default.Schedule,
            iconContentDescription = null,
            header = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.event_detail_all_day),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = form.isAllDay,
                        onCheckedChange = viewModel::setAllDay,
                        enabled = !locked,
                    )
                }
            },
        ) {
            Spacer(Modifier.height(4.dp))
            ScheduleRow(
                label = stringResource(R.string.event_edit_starts),
                dateTime = form.start,
                isAllDay = form.isAllDay,
                locale = locale,
                onDateClick = { picker = PickerTarget.StartDate },
                onTimeClick = { picker = PickerTarget.StartTime },
                enabled = !locked,
            )
            ScheduleRow(
                label = stringResource(R.string.event_edit_ends),
                dateTime = form.end,
                isAllDay = form.isAllDay,
                locale = locale,
                onDateClick = { picker = PickerTarget.EndDate },
                onTimeClick = { picker = PickerTarget.EndTime },
                isError = EventFormProblem.EndBeforeStart in state.problems,
                enabled = !locked,
            )
            if (EventFormProblem.EndBeforeStart in state.problems) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.event_edit_error_end_before_start),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            // The rows above edit a pinned event in *its own* zone, which is the
            // time it was set at — but that says nothing about when it lands for
            // whoever is reading it. Spell the local equivalent out rather than
            // leaving the user to do the offset arithmetic. Absent (null) unless
            // the event is pinned somewhere other than here.
            form.timesIn(TimeZone.currentSystemDefault())?.let { (localStart, localEnd) ->
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.event_edit_timezone_local_time,
                        formatTimeRange(localStart, localEnd, locale),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(gap))

        // Calendar card — tap anywhere to pick the target calendar. Picking a
        // different one while editing *moves* the event (copy+delete on save,
        // since CALENDAR_ID can't be updated in place). Managed special-dates
        // events stay locked: the contact sync owns their calendar.
        EditCard(
            icon = Icons.Default.CalendarMonth,
            iconContentDescription = stringResource(R.string.event_detail_calendar),
            iconTint = accent,
            onClick = { showCalendarPicker = true }
                .takeIf { state.calendars.isNotEmpty() && !state.isManaged },
        ) {
            Text(
                text = selectedCalendar?.displayName
                    ?: stringResource(R.string.event_edit_error_no_calendar),
                style = MaterialTheme.typography.titleMedium,
                color = if (selectedCalendar == null) {
                    MaterialTheme.colorScheme.error
                } else {
                    Color.Unspecified
                },
            )
            selectedCalendar?.accountName?.takeIf { it.isNotBlank() }?.let { account ->
                Text(
                    text = account,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Optional sections: which ones render by default is a setting; the
        // rest unfold behind the "more fields" button below.
        OptionalFormSection(visible = EventFormField.Timezone in state.visibleFields) {
            Spacer(Modifier.height(gap))
            EditCard(
                icon = Icons.Default.Public,
                iconContentDescription = null,
                onClick = { showTimezonePicker = true },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val pinned = remember(form.timezone, locale) {
                        form.timezone?.let { timeZoneOptionOf(it, locale, regionOf = ::icuTimeZoneRegion) }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pinned?.label
                                ?: stringResource(R.string.event_edit_timezone_device),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = pinned?.let { zoneDescriptor(it) }
                                ?: stringResource(R.string.event_edit_timezone_device_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        OptionalFormSection(visible = EventFormField.Location in state.visibleFields) {
            Spacer(Modifier.height(gap))
            EditCard(
                icon = Icons.Default.Place,
                iconContentDescription = stringResource(R.string.event_detail_location),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InlineField(
                        value = form.location,
                        onValueChange = viewModel::setLocation,
                        placeholder = stringResource(R.string.event_detail_location),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                    )
                    IconButton(
                        onClick = {
                            runCatching {
                                pickContactAddress.launch(
                                    Intent(
                                        Intent.ACTION_PICK,
                                        ContactsContract.CommonDataKinds.StructuredPostal
                                            .CONTENT_URI,
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Contacts,
                            contentDescription = stringResource(
                                R.string.event_edit_location_from_contacts,
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }

        OptionalFormSection(visible = EventFormField.Description in state.visibleFields) {
            Spacer(Modifier.height(gap))
            EditCard(
                icon = Icons.AutoMirrored.Filled.Notes,
                iconContentDescription = stringResource(R.string.event_detail_description),
                iconAtTop = true,
            ) {
                InlineField(
                    value = form.description,
                    onValueChange = viewModel::setDescription,
                    placeholder = stringResource(R.string.event_detail_description),
                    singleLine = false,
                    minLines = 3,
                )
            }
        }

        OptionalFormSection(visible = EventFormField.Reminders in state.visibleFields) {
            Spacer(Modifier.height(gap))
            // A grouped list like guests: each reminder is its own tonal card
            // with connected corners, the last row being "Add reminder".
            val reminders = form.reminders
            val rows = reminders.size + 1
            reminders.forEachIndexed { index, minutes ->
                GroupedItemCard(
                    position = positionOf(index, rows),
                    icon = Icons.Default.Notifications,
                    title = reminderLabel(minutes),
                    removeContentDescription = stringResource(R.string.event_edit_remove_reminder),
                    onRemove = { viewModel.removeReminder(minutes) },
                )
            }
            AddActionCard(
                label = stringResource(R.string.event_edit_add_reminder),
                position = positionOf(reminders.size, rows),
                onClick = { showReminderPicker = true },
            )
        }

        OptionalFormSection(visible = EventFormField.Recurrence in state.visibleFields) {
            Spacer(Modifier.height(gap))
            EditCard(
                icon = Icons.Default.Repeat,
                iconContentDescription = null,
                onClick = if (locked) null else ({ showRecurrencePicker = true }),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = form.rrule?.let { recurrenceText(it, locale) }
                                ?: AnnotatedString(stringResource(R.string.event_edit_recurrence_none)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.event_detail_recurrence),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (EventFormProblem.RecurrenceEndsBeforeStart in state.problems) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.event_edit_error_recurrence_ends_before_start),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        OptionalFormSection(visible = EventFormField.Availability in state.visibleFields) {
            Spacer(Modifier.height(gap))
            EditCard(
                icon = Icons.Default.EventAvailable,
                iconContentDescription = null,
                header = {
                    Text(
                        text = stringResource(R.string.event_edit_availability),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
            ) {
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = form.availability == Availability.Busy,
                        onClick = { viewModel.setAvailability(Availability.Busy) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text(stringResource(R.string.event_availability_busy)) }
                    SegmentedButton(
                        selected = form.availability == Availability.Free,
                        onClick = { viewModel.setAvailability(Availability.Free) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text(stringResource(R.string.event_availability_free)) }
                }
            }
        }

        OptionalFormSection(visible = EventFormField.Visibility in state.visibleFields) {
            Spacer(Modifier.height(gap))
            EditCard(
                icon = Icons.Default.Lock,
                iconContentDescription = null,
                onClick = { showVisibilityPicker = true },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(accessLevelLabel(form.accessLevel)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.event_edit_visibility),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        OptionalFormSection(visible = EventFormField.Color in state.visibleFields) {
            Spacer(Modifier.height(gap))
            // The swatch the event will paint with: its own colour, else the
            // calendar's. The Palette icon takes that colour as a preview.
            val swatch = form.color ?: selectedCalendar?.color
            EditCard(
                icon = Icons.Default.Palette,
                iconContentDescription = stringResource(R.string.event_edit_color),
                iconTint = if (colorSupported && swatch != null) {
                    eventFill(swatch, dark, soften)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                onClick = { showColorPicker = true }.takeIf { colorSupported },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                when {
                                    !colorSupported -> R.string.event_edit_color_unsupported
                                    form.color != null -> R.string.event_edit_color_custom
                                    else -> R.string.event_edit_color_default
                                },
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(
                                if (colorSupported) {
                                    R.string.event_edit_color
                                } else {
                                    R.string.event_edit_color_unsupported_hint
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (colorSyncRisk) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.event_edit_color_sync_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (colorSupported) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        OptionalFormSection(visible = EventFormField.Attendees in state.visibleFields) {
            Spacer(Modifier.height(gap))
            // Guests are an inline grouped list: each is a tonal card with a
            // role toggle + remove, and the trailing card is an inline email
            // field — type an address, press Done, it commits. Below sits an
            // honest note — Calendula never sends invitations, so whether a
            // guest is notified is the calendar backend's call.
            val guests = form.attendees
            val rows = guests.size + 1
            guests.forEachIndexed { index, attendee ->
                GuestCard(
                    attendee = attendee,
                    position = positionOf(index, rows),
                    onToggleOptional = { viewModel.setAttendeeOptional(attendee, it) },
                    onRemove = { viewModel.removeAttendee(attendee) },
                )
            }
            AddGuestInlineCard(
                position = positionOf(guests.size, rows),
                onAdd = { viewModel.addAttendee(it) },
                onPickContact = {
                    runCatching {
                        pickContact.launch(
                            Intent(
                                Intent.ACTION_PICK,
                                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                            ),
                        )
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    if (isLocalCalendar) {
                        R.string.event_edit_attendees_note_local
                    } else {
                        R.string.event_edit_attendees_note_synced
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        OptionalFormSection(visible = state.hiddenFields.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            TextButton(
                onClick = { showFieldPicker = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.event_edit_more_fields))
            }
        }
    }

    when (picker) {
        PickerTarget.StartDate -> CalendarDatePickerDialog(
            initial = form.start.date,
            onConfirm = { viewModel.setStartDate(it); picker = null },
            onDismiss = { picker = null },
        )
        PickerTarget.EndDate -> CalendarDatePickerDialog(
            initial = form.end.date,
            onConfirm = { viewModel.setEndDate(it); picker = null },
            onDismiss = { picker = null },
        )
        PickerTarget.StartTime -> TimePickerAlert(
            initial = form.start.time,
            onConfirm = { viewModel.setStartTime(it); picker = null },
            onDismiss = { picker = null },
        )
        PickerTarget.EndTime -> TimePickerAlert(
            initial = form.end.time,
            onConfirm = { viewModel.setEndTime(it); picker = null },
            onDismiss = { picker = null },
        )
        null -> Unit
    }

    if (showCalendarPicker) {
        CalendarPicker(
            calendars = state.calendars,
            selectedId = form.calendarId,
            onSelect = {
                viewModel.setCalendar(it)
                showCalendarPicker = false
            },
            onDismiss = { showCalendarPicker = false },
        )
    }

    if (showReminderPicker) {
        ReminderPickerDialog(
            alreadyChosen = form.reminders,
            onSelect = { minutes ->
                viewModel.addReminder(minutes)
                showReminderPicker = false
            },
            onDismiss = { showReminderPicker = false },
        )
    }


    if (showRecurrencePicker) {
        RecurrencePickerDialog(
            current = form.rrule,
            startDay = form.start.date.dayOfWeek,
            onSelect = { rrule ->
                viewModel.setRecurrence(rrule)
                showRecurrencePicker = false
            },
            onDismiss = { showRecurrencePicker = false },
        )
    }

    if (showVisibilityPicker) {
        VisibilityPickerDialog(
            selected = form.accessLevel,
            onSelect = { level ->
                viewModel.setAccessLevel(level)
                showVisibilityPicker = false
            },
            onDismiss = { showVisibilityPicker = false },
        )
    }

    if (showTimezonePicker) {
        TimeZonePickerDialog(
            selected = form.timezone,
            deviceZoneId = ZoneId.systemDefault().id,
            recents = state.recentTimeZones,
            onSelect = viewModel::setTimezone,
            onDismiss = { showTimezonePicker = false },
        )
    }

    if (showColorPicker) {
        ColorPickerDialog(
            palette = state.colorPalette,
            selected = form.color,
            hasExplicitColor = form.color != null,
            syncWarning = colorSyncRisk,
            onPickKey = { key, argb ->
                viewModel.setColorKey(key, argb)
                showColorPicker = false
            },
            onPickRaw = { argb ->
                viewModel.setColorRaw(argb)
                showColorPicker = false
            },
            onClear = {
                viewModel.clearColor()
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false },
        )
    }

    if (showFieldPicker) {
        FieldPickerDialog(
            hiddenFields = state.hiddenFields,
            onSelect = { field ->
                viewModel.revealField(field)
                showFieldPicker = false
            },
            onDismiss = { showFieldPicker = false },
        )
    }
}

/** Picks one hidden optional section to add to the form. */
@Composable
private fun FieldPickerDialog(
    hiddenFields: List<EventFormField>,
    onSelect: (EventFormField) -> Unit,
    onDismiss: () -> Unit,
) {
    FullScreenPicker(
        title = stringResource(R.string.event_edit_more_fields),
        onDismiss = onDismiss,
    ) {
        hiddenFields.forEachIndexed { index, field ->
            GroupedRow(
                title = stringResource(eventFormFieldLabel(field)),
                position = positionOf(index, hiddenFields.size),
                leading = { Icon(imageVector = eventFormFieldIcon(field), contentDescription = null) },
                onClick = { onSelect(field) },
            )
        }
    }
}

/** Quick-pick lead times offered as chips in the reminder dialog. */
private val REMINDER_QUICK_PICKS = REMINDER_PRESETS

/**
 * Reminder picker, two steps: the common lead times as a tappable list
 * (one tap adds and closes), with "Custom" at the bottom switching to an
 * amount-plus-unit editor — only that step needs an Add button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderPickerDialog(
    alreadyChosen: List<Int>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var customMode by rememberSaveable { mutableStateOf(false) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf(ReminderUnit.Minutes) }
    val customMinutes = amountText.toIntOrNull()
        ?.takeIf { it in 1..999 }
        ?.let { it * unit.minutesFactor }

    FullScreenPicker(
        title = stringResource(R.string.event_edit_add_reminder),
        onDismiss = onDismiss,
        actions = {
            // The quick-pick list adds on tap; only the custom step needs Add.
            if (customMode) {
                TextButton(
                    enabled = customMinutes != null && customMinutes !in alreadyChosen,
                    onClick = { customMinutes?.let(onSelect) },
                ) { Text(stringResource(R.string.event_edit_add)) }
            }
        },
    ) {
        if (!customMode) {
            val presets = REMINDER_QUICK_PICKS.filterNot { it in alreadyChosen }
            val rowCount = presets.size + 1
            presets.forEachIndexed { index, minutes ->
                GroupedRow(
                    title = reminderLabel(minutes),
                    position = positionOf(index, rowCount),
                    onClick = { onSelect(minutes) },
                )
            }
            GroupedRow(
                title = stringResource(R.string.event_edit_reminder_custom),
                position = positionOf(rowCount - 1, rowCount),
                onClick = { customMode = true },
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                DialogAmountField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    placeholder = "10",
                )
                Spacer(Modifier.width(12.dp))
                DialogUnitDropdown(
                    label = stringResource(reminderUnitLabel(unit)),
                    entries = ReminderUnit.entries.map { stringResource(reminderUnitLabel(it)) },
                    onPick = { unit = ReminderUnit.entries[it] },
                )
            }
        }
    }
}

/** How a custom recurrence ends; mirrors [RecurrenceEnd] in saveable form. */
private enum class RecurrenceEndMode { Never, Until, Count }

/**
 * Recurrence picker, two steps like the reminder picker: the plain
 * frequencies as a tappable list (one tap applies and closes), with "Custom"
 * switching to an interval-plus-unit editor, weekday toggles (weekly only)
 * and an end condition — only that step needs an OK button. A rule the
 * simple shape can't express (ordinal BYDAY etc.) stays untouched unless the
 * user picks something here.
 */
@Composable
private fun RecurrencePickerDialog(
    current: String?,
    startDay: DayOfWeek,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val parsed = remember(current) { current?.let(::parseSimpleRecurrence) }
    val isPlainPreset = parsed != null && parsed.interval == 1 &&
        parsed.end == RecurrenceEnd.Never && parsed.byDays.isEmpty()
    var customMode by rememberSaveable { mutableStateOf(false) }
    var intervalText by rememberSaveable { mutableStateOf((parsed?.interval ?: 1).toString()) }
    var freq by rememberSaveable { mutableStateOf(parsed?.freq ?: RecurrenceFreq.Weekly) }
    // The event's start weekday stands in until the user picks days herself.
    var daysMask by rememberSaveable {
        mutableStateOf(
            (parsed?.byDays?.takeIf { it.isNotEmpty() } ?: setOf(startDay)).toMask(),
        )
    }
    var endMode by rememberSaveable {
        mutableStateOf(
            when (parsed?.end) {
                is RecurrenceEnd.Until -> RecurrenceEndMode.Until
                is RecurrenceEnd.Count -> RecurrenceEndMode.Count
                else -> RecurrenceEndMode.Never
            },
        )
    }
    var untilIso by rememberSaveable {
        mutableStateOf((parsed?.end as? RecurrenceEnd.Until)?.date?.toString())
    }
    var countText by rememberSaveable {
        mutableStateOf(((parsed?.end as? RecurrenceEnd.Count)?.times ?: 10).toString())
    }
    var showUntilPicker by rememberSaveable { mutableStateOf(false) }

    val locale = currentLocale()
    val untilDate = untilIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val interval = intervalText.toIntOrNull()?.takeIf { it in 1..999 }
    val count = countText.toIntOrNull()?.takeIf { it in 1..999 }
    val customEnd: RecurrenceEnd? = when (endMode) {
        RecurrenceEndMode.Never -> RecurrenceEnd.Never
        RecurrenceEndMode.Until -> untilDate?.let { RecurrenceEnd.Until(it) }
        RecurrenceEndMode.Count -> count?.let { RecurrenceEnd.Count(it) }
    }
    val customResult: String? = if (interval != null && customEnd != null) {
        SimpleRecurrence(
            freq = freq,
            interval = interval,
            end = customEnd,
            byDays = if (freq == RecurrenceFreq.Weekly) daysMask.toDaySet() else emptySet(),
        ).toRRule()
    } else {
        null
    }

    FullScreenPicker(
        title = stringResource(R.string.event_detail_recurrence),
        onDismiss = onDismiss,
        actions = {
            // The preset list applies on tap; only the custom step needs OK.
            if (customMode) {
                TextButton(
                    enabled = customResult != null,
                    onClick = { customResult?.let(onSelect) },
                ) { Text(stringResource(R.string.dialog_ok)) }
            }
        },
    ) {
        if (!customMode) {
            val rowCount = RecurrenceFreq.entries.size + 2
            GroupedRow(
                title = stringResource(R.string.event_edit_recurrence_none),
                position = positionOf(0, rowCount),
                selected = current == null,
                onClick = { onSelect(null) },
            )
            RecurrenceFreq.entries.forEachIndexed { index, entry ->
                GroupedRow(
                    title = stringResource(recurrencePresetLabel(entry)),
                    position = positionOf(index + 1, rowCount),
                    selected = isPlainPreset && parsed?.freq == entry,
                    onClick = { onSelect(SimpleRecurrence(entry).toRRule()) },
                )
            }
            GroupedRow(
                title = stringResource(R.string.event_edit_recurrence_custom),
                position = positionOf(rowCount - 1, rowCount),
                selected = current != null && !isPlainPreset,
                onClick = { customMode = true },
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.event_edit_recurrence_every),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.width(12.dp))
                    DialogAmountField(
                        value = intervalText,
                        onValueChange = { intervalText = it },
                        placeholder = "1",
                    )
                    Spacer(Modifier.width(12.dp))
                    DialogUnitDropdown(
                        label = stringResource(recurrenceUnitLabel(freq)),
                        entries = RecurrenceFreq.entries.map {
                            stringResource(recurrenceUnitLabel(it))
                        },
                        onPick = { freq = RecurrenceFreq.entries[it] },
                    )
                }
                if (freq == RecurrenceFreq.Weekly) {
                    WeekdayToggleRow(
                        selected = daysMask.toDaySet(),
                        onToggle = { day -> daysMask = daysMask xor day.toMaskBit() },
                        locale = locale,
                    )
                }
                Text(
                    text = stringResource(R.string.event_edit_recurrence_ends),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            GroupedRow(
                title = stringResource(R.string.event_edit_recurrence_end_never),
                position = positionOf(0, 3),
                selected = endMode == RecurrenceEndMode.Never,
                onClick = { endMode = RecurrenceEndMode.Never },
            )
            GroupedRow(
                title = stringResource(R.string.event_edit_recurrence_end_until),
                summary = untilDate?.let {
                    remember(it, locale) {
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                            .withLocale(locale).format(it.toJavaLocalDate())
                    }
                },
                position = positionOf(1, 3),
                selected = endMode == RecurrenceEndMode.Until,
                onClick = {
                    endMode = RecurrenceEndMode.Until
                    showUntilPicker = true
                },
            )
            GroupedRow(
                title = stringResource(R.string.event_edit_recurrence_end_count),
                position = positionOf(2, 3),
                selected = endMode == RecurrenceEndMode.Count,
                onClick = { endMode = RecurrenceEndMode.Count },
            )
            if (endMode == RecurrenceEndMode.Count) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                ) {
                    DialogAmountField(
                        value = countText,
                        onValueChange = { countText = it },
                        placeholder = "10",
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.event_edit_recurrence_times),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }

    if (showUntilPicker) {
        CalendarDatePickerDialog(
            initial = untilDate ?: LocalDate.fromEpochDays(
                (Clock.System.now().toEpochMilliseconds() / MILLIS_PER_DAY).toInt(),
            ),
            onConfirm = {
                untilIso = it.toString()
                showUntilPicker = false
            },
            onDismiss = { showUntilPicker = false },
        )
    }
}

/**
 * One tappable circle per weekday (locale week order), multi-select — the
 * BYDAY picks of a weekly rule. Deselecting every day is allowed; the rule
 * then falls back to the event's start weekday (provider behaviour).
 */
@Composable
private fun WeekdayToggleRow(
    selected: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
    locale: Locale,
) {
    val days = remember(locale) {
        val first = WeekFields.of(locale).firstDayOfWeek.toKotlinDayOfWeek()
        (0 until 7).map { DayOfWeek(((first.isoDayNumber - 1 + it) % 7) + 1) }
    }
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        days.forEach { day ->
            val isSelected = day in selected
            Surface(
                onClick = { onToggle(day) },
                shape = CircleShape,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = day.toJavaDayOfWeek().getDisplayName(JavaTextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/** Weekday sets travel through rememberSaveable as an ISO-day bitmask. */
private fun DayOfWeek.toMaskBit(): Int = 1 shl (isoDayNumber - 1)

private fun Set<DayOfWeek>.toMask(): Int = fold(0) { acc, day -> acc or day.toMaskBit() }

private fun Int.toDaySet(): Set<DayOfWeek> =
    DayOfWeek.entries.filter { this and it.toMaskBit() != 0 }.toSet()


private fun recurrencePresetLabel(freq: RecurrenceFreq): Int = when (freq) {
    RecurrenceFreq.Daily -> R.string.recurrence_daily
    RecurrenceFreq.Weekly -> R.string.recurrence_weekly
    RecurrenceFreq.Monthly -> R.string.recurrence_monthly
    RecurrenceFreq.Yearly -> R.string.recurrence_yearly
}

private fun recurrenceUnitLabel(freq: RecurrenceFreq): Int = when (freq) {
    RecurrenceFreq.Daily -> R.string.recurrence_unit_days
    RecurrenceFreq.Weekly -> R.string.recurrence_unit_weeks
    RecurrenceFreq.Monthly -> R.string.recurrence_unit_months
    RecurrenceFreq.Yearly -> R.string.recurrence_unit_years
}

/** Corner shape for a card at [position] in a grouped run (mirrors GroupedRow). */
private fun groupedShape(position: Position, full: Dp = 20.dp, small: Dp = 6.dp): Shape =
    when (position) {
        Position.Alone -> RoundedCornerShape(full)
        Position.Top -> RoundedCornerShape(
            topStart = full, topEnd = full, bottomStart = small, bottomEnd = small,
        )
        Position.Middle -> RoundedCornerShape(small)
        Position.Bottom -> RoundedCornerShape(
            topStart = small, topEnd = small, bottomStart = full, bottomEnd = full,
        )
    }

/** The 2dp gap that visually separates grouped cards (none after the last). */
private fun groupedGap(position: Position): Modifier = when (position) {
    Position.Top, Position.Middle -> Modifier.padding(bottom = 2.dp)
    Position.Bottom, Position.Alone -> Modifier
}

/**
 * One entry in a grouped form list (a guest, a reminder): a leading [icon],
 * a [title] with optional [supporting] line, and a remove button — all on a
 * tonal card whose corners come from its [position] in the run.
 */
@Composable
private fun GroupedItemCard(
    position: Position,
    icon: ImageVector,
    title: String,
    removeContentDescription: String,
    onRemove: () -> Unit,
    supporting: String? = null,
    titleMaxLines: Int = Int.MAX_VALUE,
    trailing: @Composable (() -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = groupedShape(position),
        modifier = Modifier
            .fillMaxWidth()
            .then(groupedGap(position)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = titleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = removeContentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * One guest as a grouped-list card: avatar glyph, name/email, a tappable
 * Required/Optional role chip, and remove. The email shows as the supporting
 * line when a name is present, otherwise as the title.
 */
@Composable
private fun GuestCard(
    attendee: EventAttendee,
    position: Position,
    onToggleOptional: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val hasName = attendee.name.isNotBlank()
    GroupedItemCard(
        position = position,
        icon = Icons.Default.Person,
        title = if (hasName) attendee.name else attendee.email,
        supporting = attendee.email.takeIf { hasName },
        titleMaxLines = 1,
        removeContentDescription = stringResource(R.string.event_edit_remove_guest),
        onRemove = onRemove,
        trailing = {
            FilterChip(
                selected = attendee.optional,
                onClick = { onToggleOptional(!attendee.optional) },
                label = {
                    Text(
                        stringResource(
                            if (attendee.optional) {
                                R.string.event_edit_attendee_optional
                            } else {
                                R.string.event_edit_attendee_required
                            },
                        ),
                    )
                },
            )
        },
    )
}

/** The trailing "Add …" action card of a grouped form list (opens a picker). */
@Composable
private fun AddActionCard(label: String, position: Position, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = groupedShape(position),
        modifier = Modifier
            .fillMaxWidth()
            .then(groupedGap(position)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * The trailing card of the guests list: an inline email field plus a "from
 * contacts" button. Type an address and press Done (or tap +) to commit a guest;
 * or pick from contacts to fill email + name at once. The field clears after,
 * ready for the next. Matches the form's inline-field input style — no dialog.
 */
@Composable
private fun AddGuestInlineCard(
    position: Position,
    onAdd: (String) -> Unit,
    onPickContact: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val valid = remember(text) { Patterns.EMAIL_ADDRESS.matcher(text.trim()).matches() }
    fun commit() {
        if (valid) {
            onAdd(text.trim())
            text = ""
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = groupedShape(position),
        modifier = Modifier
            .fillMaxWidth()
            .then(groupedGap(position)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            InlineTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = stringResource(R.string.event_edit_add_guest_hint),
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
                onImeAction = { commit() },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
            )
            if (valid) {
                IconButton(onClick = { commit() }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.event_edit_add_guest),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            IconButton(onClick = onPickContact, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.Contacts,
                    contentDescription = stringResource(R.string.event_edit_add_guest_from_contacts),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * Read the email + display name from a contact-pick result URI. No
 * READ_CONTACTS needed: ACTION_PICK grants this URI temporary read access.
 */
private fun readContactEmail(context: Context, uri: Uri): Pair<String, String>? =
    context.contentResolver.query(
        uri,
        arrayOf(
            ContactsContract.CommonDataKinds.Email.ADDRESS,
            ContactsContract.Contacts.DISPLAY_NAME,
        ),
        null, null, null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val email = cursor.getString(0).orEmpty().trim()
        val name = cursor.getString(1).orEmpty().trim()
        email.takeIf { it.isNotEmpty() }?.let { it to name }
    }

/**
 * Read the formatted postal address from a contact-pick result URI. No
 * READ_CONTACTS needed: ACTION_PICK grants this URI temporary read access.
 * The provider's formatted address is multi-line; collapse it to one line so
 * it sits cleanly in the single-line location field.
 */
private fun readContactAddress(context: Context, uri: Uri): String? =
    context.contentResolver.query(
        uri,
        arrayOf(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS),
        null, null, null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        cursor.getString(0).orEmpty()
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
            .takeIf { it.isNotEmpty() }
    }


/**
 * Visibility selector: one card per level, each with its own icon; the
 * current level is highlighted. Tap picks and closes.
 */
@Composable
private fun VisibilityPickerDialog(
    selected: AccessLevel,
    onSelect: (AccessLevel) -> Unit,
    onDismiss: () -> Unit,
) {
    OptionPicker(
        title = stringResource(R.string.event_edit_visibility),
        options = AccessLevel.entries.toList(),
        selected = selected,
        label = { stringResource(accessLevelLabel(it)) },
        onSelect = onSelect,
        onDismiss = onDismiss,
        leading = { Icon(imageVector = accessLevelIcon(it), contentDescription = null) },
    )
}

/**
 * Event-colour picker: just the swatches. A non-empty [palette] (the calendar
 * account's published colours) picks by key so the colour round-trips through
 * sync; otherwise the app's own palette writes a raw colour, with a
 * [syncWarning] when that calendar may not keep it. The "Reset" button (shown
 * only once a colour is set) drops back to the calendar's own colour.
 */
@Composable
private fun ColorPickerDialog(
    palette: List<EventColorOption>,
    selected: Int?,
    hasExplicitColor: Boolean,
    syncWarning: Boolean,
    onPickKey: (String, Int) -> Unit,
    onPickRaw: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    FullScreenPicker(
        title = stringResource(R.string.event_edit_color),
        onDismiss = onDismiss,
        actions = {
            if (hasExplicitColor) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.event_edit_color_reset))
                }
            }
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            if (palette.isNotEmpty()) {
                // The event's current colour may not be in the curated palette
                // (a thinned near-duplicate, or a raw colour set elsewhere) —
                // append it so the selection ring has a home.
                val swatches = palette.map { it.argb }.let {
                    if (selected != null && selected !in it) it + selected else it
                }
                ColorSwatchRow(
                    colors = swatches,
                    selected = selected,
                    onSelect = { argb ->
                        val option = palette.firstOrNull { it.argb == argb }
                        // The appended current colour has no provider key to
                        // write — it is already the event's colour, so just
                        // close.
                        if (option != null) onPickKey(option.key, option.argb) else onDismiss()
                    },
                    dark = dark,
                )
            } else {
                ColorSwatchRow(
                    colors = CalendarColorPalette.all,
                    selected = selected,
                    onSelect = onPickRaw,
                    dark = dark,
                )
                if (syncWarning) {
                    Text(
                        text = stringResource(R.string.event_edit_color_sync_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun accessLevelIcon(level: AccessLevel): ImageVector = when (level) {
    AccessLevel.Default -> Icons.Default.Tune
    AccessLevel.Public -> Icons.Default.Public
    AccessLevel.Private -> Icons.Default.Lock
    AccessLevel.Confidential -> Icons.Default.VisibilityOff
}

private fun accessLevelLabel(level: AccessLevel): Int = when (level) {
    AccessLevel.Default -> R.string.event_access_default
    AccessLevel.Public -> R.string.event_access_public
    AccessLevel.Private -> R.string.event_access_private
    AccessLevel.Confidential -> R.string.event_access_confidential
}

/** Humanise a reminder lead time, mirroring the detail screen's rendering. */
@Composable
private fun reminderLabel(minutes: Int): String = reminderLeadTimeLabel(minutes)

/**
 * One info card mirroring the detail screen's DetailCard: tonal container,
 * leading icon in the gutter, value to the right. Optionally clickable as a
 * whole (calendar picker).
 */
@Composable
private fun EditCard(
    icon: ImageVector,
    iconContentDescription: String?,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    // Multiline-input cards align the icon with the field's first text line.
    iconAtTop: Boolean = false,
    // Tall cards pass their first row here: the icon centres on it, and
    // [content] continues below, indented to the same text column.
    header: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val color = MaterialTheme.colorScheme.surfaceContainerHigh
    val inner: @Composable () -> Unit = {
        if (header != null) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = iconContentDescription,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f), content = header)
                }
                Column(
                    modifier = Modifier.padding(start = 40.dp),
                    content = content,
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = if (iconAtTop) Alignment.Top else Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconContentDescription,
                    tint = iconTint,
                    // 4dp mirrors InlineField's vertical padding, so a
                    // top-aligned icon (24dp) centres on the ~24sp first line.
                    modifier = Modifier
                        .padding(top = if (iconAtTop) 4.dp else 0.dp)
                        .size(24.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f), content = content)
            }
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            color = color,
            shape = shape,
            modifier = Modifier.fillMaxWidth(),
        ) { inner() }
    } else {
        Surface(
            color = color,
            shape = shape,
            modifier = Modifier.fillMaxWidth(),
        ) { inner() }
    }
}

/**
 * "2:00 PM – 3:00 PM", honouring the user's 12/24-hour setting. Collapses to one
 * time when both land on the same minute (an instant event shouldn't read as a
 * range against itself). Dates are deliberately absent — the rows above carry
 * them.
 */
@Composable
private fun formatTimeRange(start: LocalDateTime, end: LocalDateTime, locale: Locale): String {
    val use24Hour = LocalUse24HourFormat.current
    val timeFormat = remember(locale, use24Hour) { timeOfDayFormatter(use24Hour, locale) }
    val from = timeFormat.format(start.time.toJavaLocalTime())
    val to = timeFormat.format(end.time.toJavaLocalTime())
    return if (from == to) from else "$from – $to"
}

/**
 * Borderless text input used inside the cards (and as the headline title).
 * Thin wrapper over the shared [InlineTextField] so the form and the rest of
 * the app share one input style.
 */
@Composable
private fun InlineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
) {
    InlineTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        textStyle = textStyle,
        singleLine = singleLine,
        minLines = minLines,
        enabled = enabled,
        keyboardType = keyboardType,
        capitalization = KeyboardCapitalization.None,
    )
}

/** One schedule row: label, then tappable date and (unless all-day) time. */
@Composable
private fun ScheduleRow(
    label: String,
    dateTime: LocalDateTime,
    isAllDay: Boolean,
    locale: Locale,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    val dateFormat = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    val use24Hour = LocalUse24HourFormat.current
    val timeFormat = remember(locale, use24Hour) {
        timeOfDayFormatter(use24Hour, locale)
    }
    // Tappable values read as links (primary), like the location on the
    // detail screen; errors flip them to the error colour; a locked (managed)
    // row dims to a plain, non-tappable value.
    val valueColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = dateFormat.format(dateTime.date.toJavaLocalDate()),
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
            modifier = Modifier
                .clickable(enabled = enabled, onClick = onDateClick)
                .padding(vertical = 8.dp, horizontal = 6.dp),
        )
        if (!isAllDay) {
            Text(
                text = timeFormat.format(dateTime.time.toJavaLocalTime()),
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
                modifier = Modifier
                    .clickable(enabled = enabled, onClick = onTimeClick)
                    .padding(vertical = 8.dp, horizontal = 6.dp),
            )
        }
    }
}

/**
 * Full-screen calendar picker: a scrollable list of every writable calendar,
 * grouped under its owning account like the visibility filter and calendar
 * manager. Replaces the former fixed-height [AlertDialog], which clipped the
 * list past ~9 rows so accounts with many calendars had unreachable entries
 * (#29). Selecting a row calls [onSelect]; the caller closes the picker.
 */
@Composable
private fun CalendarPicker(
    calendars: List<CalendarSource>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    FullScreenPicker(
        title = stringResource(R.string.event_detail_calendar),
        onDismiss = onDismiss,
    ) {
        CalendarPickerGroups(
            calendars = calendars,
            selectedId = selectedId,
            onSelect = onSelect,
        )
    }
}
