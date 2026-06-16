package de.jeanlucmakiola.calendula.ui.edit

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.AccessLevel
import de.jeanlucmakiola.calendula.domain.Availability
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventFormField
import de.jeanlucmakiola.calendula.domain.EventFormProblem
import de.jeanlucmakiola.calendula.domain.RecurrenceEnd
import de.jeanlucmakiola.calendula.domain.RecurrenceFreq
import de.jeanlucmakiola.calendula.domain.RecurringWriteScope
import de.jeanlucmakiola.calendula.domain.SimpleRecurrence
import de.jeanlucmakiola.calendula.domain.parseSimpleRecurrence
import de.jeanlucmakiola.calendula.domain.toRRule
import de.jeanlucmakiola.calendula.ui.common.InlineTextField
import de.jeanlucmakiola.calendula.ui.common.OptionCard
import de.jeanlucmakiola.calendula.ui.common.currentLocale
import de.jeanlucmakiola.calendula.ui.common.pastelize
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
    viewModel: EventEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(initialDateIso, editKey) {
        if (editKey != null) {
            viewModel.openForEdit(
                eventId = editKey[0],
                beginMillis = editKey[1],
                endMillis = editKey[2],
            )
        } else {
            val date = initialDateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            viewModel.openNew(date, initialStartMinutes)
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadFailed by viewModel.loadFailed.collectAsStateWithLifecycle()

    // The form is intentionally forgotten on every close (cancel or save) so
    // the next open starts clean; it survives rotation because openNew /
    // openForEdit no-op while a form is set.
    val close = {
        viewModel.reset()
        onClose()
    }
    BackHandler(onBack = close)

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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.event_edit_conflict_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.event_edit_conflict_body))
                Spacer(Modifier.height(4.dp))
                OptionCard(
                    label = stringResource(R.string.event_edit_conflict_overwrite),
                    supportingText = stringResource(R.string.event_edit_conflict_overwrite_hint),
                    onClick = onOverwrite,
                )
                OptionCard(
                    label = stringResource(R.string.event_edit_conflict_discard),
                    supportingText = stringResource(R.string.event_edit_conflict_discard_hint),
                    onClick = onDiscard,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OptionalFormSection(
    visible: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()) +
            fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) +
            fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
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
    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    var showCalendarPicker by rememberSaveable { mutableStateOf(false) }
    var showReminderPicker by rememberSaveable { mutableStateOf(false) }
    var showRecurrencePicker by rememberSaveable { mutableStateOf(false) }
    var showVisibilityPicker by rememberSaveable { mutableStateOf(false) }
    var showFieldPicker by rememberSaveable { mutableStateOf(false) }

    val selectedCalendar = state.calendars.firstOrNull { it.id == form.calendarId }
    // The accent ties the form to the detail screen's design language: the
    // bar under the title takes the target calendar's colour.
    val accent = selectedCalendar?.let { pastelize(it.color, dark) }
        ?: MaterialTheme.colorScheme.primary
    val gap = 12.dp

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
        // accent bar instead of a boxed Material text field.
        InlineField(
            value = form.title,
            onValueChange = viewModel::setTitle,
            placeholder = stringResource(R.string.event_edit_title_hint),
            textStyle = MaterialTheme.typography.headlineMedium
                .copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(3.dp)
                .background(accent, RoundedCornerShape(2.dp)),
        )

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
                    Switch(checked = form.isAllDay, onCheckedChange = viewModel::setAllDay)
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
            )
            ScheduleRow(
                label = stringResource(R.string.event_edit_ends),
                dateTime = form.end,
                isAllDay = form.isAllDay,
                locale = locale,
                onDateClick = { picker = PickerTarget.EndDate },
                onTimeClick = { picker = PickerTarget.EndTime },
                isError = EventFormProblem.EndBeforeStart in state.problems,
            )
            if (EventFormProblem.EndBeforeStart in state.problems) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.event_edit_error_end_before_start),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(Modifier.height(gap))

        // Calendar card — tap anywhere to pick the target calendar. Editing
        // keeps the owning calendar (moving events between calendars is a
        // sync-adapter minefield; every stock calendar app locks it too).
        EditCard(
            icon = Icons.Default.CalendarMonth,
            iconContentDescription = stringResource(R.string.event_detail_calendar),
            iconTint = accent,
            onClick = { showCalendarPicker = true }
                .takeIf { state.calendars.isNotEmpty() && !state.isEditing },
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
        OptionalFormSection(visible = EventFormField.Location in state.visibleFields) {
            Spacer(Modifier.height(gap))
            EditCard(
                icon = Icons.Default.Place,
                iconContentDescription = stringResource(R.string.event_detail_location),
            ) {
                InlineField(
                    value = form.location,
                    onValueChange = viewModel::setLocation,
                    placeholder = stringResource(R.string.event_detail_location),
                )
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
            // Reminders stack one per row (remove on the right edge); the
            // add chip closes the card at the bottom. The card icon centres
            // on the first row, whatever that is.
            EditCard(
                icon = Icons.Default.Notifications,
                iconContentDescription = stringResource(R.string.event_detail_reminders),
                header = {
                    when (val first = form.reminders.firstOrNull()) {
                        null -> AddReminderChip(onClick = { showReminderPicker = true })
                        else -> ReminderRow(
                            label = reminderLabel(first),
                            onRemove = { viewModel.removeReminder(first) },
                        )
                    }
                },
            ) {
                form.reminders.drop(1).forEach { minutes ->
                    ReminderRow(
                        label = reminderLabel(minutes),
                        onRemove = { viewModel.removeReminder(minutes) },
                    )
                }
                if (form.reminders.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    AddReminderChip(onClick = { showReminderPicker = true })
                }
            }
        }

        OptionalFormSection(visible = EventFormField.Recurrence in state.visibleFields) {
            Spacer(Modifier.height(gap))
            EditCard(
                icon = Icons.Default.Repeat,
                iconContentDescription = null,
                onClick = { showRecurrencePicker = true },
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
        PickerTarget.StartDate -> DatePickerAlert(
            initial = form.start.date,
            onConfirm = { viewModel.setStartDate(it); picker = null },
            onDismiss = { picker = null },
        )
        PickerTarget.EndDate -> DatePickerAlert(
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
        CalendarPickerDialog(
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.event_edit_more_fields)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                hiddenFields.forEach { field ->
                    OptionCard(
                        label = stringResource(fieldLabel(field)),
                        onClick = { onSelect(field) },
                        icon = fieldIcon(field),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

/** Quick-pick lead times offered as chips in the reminder dialog. */
private val REMINDER_QUICK_PICKS = listOf(0, 10, 30, 60, 1_440)

private enum class ReminderUnit(val minutesFactor: Int) {
    Minutes(1),
    Hours(60),
    Days(1_440),
    Weeks(10_080),
}

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.event_edit_add_reminder)) },
        text = {
            if (!customMode) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    REMINDER_QUICK_PICKS.filterNot { it in alreadyChosen }.forEach { minutes ->
                        OptionCard(
                            label = reminderLabel(minutes),
                            onClick = { onSelect(minutes) },
                        )
                    }
                    OptionCard(
                        label = stringResource(R.string.event_edit_reminder_custom),
                        onClick = { customMode = true },
                        labelColor = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
        },
        confirmButton = {
            // The quick-pick list adds on tap; only the custom step needs Add.
            if (customMode) {
                TextButton(
                    enabled = customMinutes != null && customMinutes !in alreadyChosen,
                    onClick = { customMinutes?.let(onSelect) },
                ) { Text(stringResource(R.string.event_edit_add)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.event_detail_recurrence)) },
        text = {
            if (!customMode) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionCard(
                        label = stringResource(R.string.event_edit_recurrence_none),
                        onClick = { onSelect(null) },
                        selected = current == null,
                    )
                    RecurrenceFreq.entries.forEach { entry ->
                        OptionCard(
                            label = stringResource(recurrencePresetLabel(entry)),
                            onClick = { onSelect(SimpleRecurrence(entry).toRRule()) },
                            selected = isPlainPreset && parsed?.freq == entry,
                        )
                    }
                    OptionCard(
                        label = stringResource(R.string.event_edit_recurrence_custom),
                        onClick = { customMode = true },
                        selected = current != null && !isPlainPreset,
                        labelColor = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Spacer(Modifier.height(4.dp))
                        WeekdayToggleRow(
                            selected = daysMask.toDaySet(),
                            onToggle = { day -> daysMask = daysMask xor day.toMaskBit() },
                            locale = locale,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.event_edit_recurrence_ends),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OptionCard(
                        label = stringResource(R.string.event_edit_recurrence_end_never),
                        onClick = { endMode = RecurrenceEndMode.Never },
                        selected = endMode == RecurrenceEndMode.Never,
                    )
                    OptionCard(
                        label = stringResource(R.string.event_edit_recurrence_end_until),
                        onClick = {
                            endMode = RecurrenceEndMode.Until
                            showUntilPicker = true
                        },
                        supportingText = untilDate?.let {
                            remember(it, locale) {
                                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                                    .withLocale(locale).format(it.toJavaLocalDate())
                            }
                        },
                        selected = endMode == RecurrenceEndMode.Until,
                    )
                    OptionCard(
                        label = stringResource(R.string.event_edit_recurrence_end_count),
                        onClick = { endMode = RecurrenceEndMode.Count },
                        selected = endMode == RecurrenceEndMode.Count,
                    )
                    if (endMode == RecurrenceEndMode.Count) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
        },
        confirmButton = {
            // The preset list applies on tap; only the custom step needs OK.
            if (customMode) {
                TextButton(
                    enabled = customResult != null,
                    onClick = { customResult?.let(onSelect) },
                ) { Text(stringResource(R.string.dialog_ok)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )

    if (showUntilPicker) {
        DatePickerAlert(
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

/** Tonal 3-digit number input shared by the custom reminder/recurrence steps. */
@Composable
private fun DialogAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    // surfaceContainerHighest — the dialog itself sits on
    // surfaceContainerHigh, so anything lower vanishes.
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(12.dp),
    ) {
        InlineField(
            value = value,
            onValueChange = { text ->
                if (text.length <= 3 && text.all(Char::isDigit)) {
                    onValueChange(text)
                }
            },
            placeholder = placeholder,
            textStyle = MaterialTheme.typography.titleMedium,
            keyboardType = KeyboardType.Number,
            modifier = Modifier
                .width(72.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

/** Tonal dropdown trigger + menu shared by the custom reminder/recurrence steps. */
@Composable
private fun DialogUnitDropdown(
    label: String,
    entries: List<String>,
    onPick: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(12.dp),
            onClick = { open = true },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(
                    start = 14.dp,
                    end = 8.dp,
                    top = 12.dp,
                    bottom = 12.dp,
                ),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            entries.forEachIndexed { index, entry ->
                DropdownMenuItem(
                    text = { Text(entry) },
                    onClick = {
                        onPick(index)
                        open = false
                    },
                )
            }
        }
    }
}

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

/** One chosen reminder: humanised lead time, remove pinned to the right edge. */
@Composable
private fun ReminderRow(label: String, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.event_edit_remove_reminder),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun AddReminderChip(onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.event_edit_add)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        border = null,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun reminderUnitLabel(unit: ReminderUnit): Int = when (unit) {
    ReminderUnit.Minutes -> R.string.reminder_unit_minutes
    ReminderUnit.Hours -> R.string.reminder_unit_hours
    ReminderUnit.Days -> R.string.reminder_unit_days
    ReminderUnit.Weeks -> R.string.reminder_unit_weeks
}

private fun fieldLabel(field: EventFormField): Int = when (field) {
    EventFormField.Location -> R.string.event_detail_location
    EventFormField.Description -> R.string.event_detail_description
    EventFormField.Reminders -> R.string.event_detail_reminders
    EventFormField.Recurrence -> R.string.event_detail_recurrence
    EventFormField.Availability -> R.string.event_edit_availability
    EventFormField.Visibility -> R.string.event_edit_visibility
}

private fun fieldIcon(field: EventFormField): ImageVector = when (field) {
    EventFormField.Location -> Icons.Default.Place
    EventFormField.Description -> Icons.AutoMirrored.Filled.Notes
    EventFormField.Reminders -> Icons.Default.Notifications
    EventFormField.Recurrence -> Icons.Default.Repeat
    EventFormField.Availability -> Icons.Default.EventAvailable
    EventFormField.Visibility -> Icons.Default.Lock
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.event_edit_visibility)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AccessLevel.entries.forEach { level ->
                    OptionCard(
                        label = stringResource(accessLevelLabel(level)),
                        onClick = { onSelect(level) },
                        icon = accessLevelIcon(level),
                        selected = level == selected,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
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
private fun reminderLabel(minutes: Int): String = when {
    minutes <= 0 -> stringResource(R.string.reminder_at_time)
    minutes % 10_080 == 0 ->
        pluralStringResource(R.plurals.reminder_weeks, minutes / 10_080, minutes / 10_080)
    minutes % 1_440 == 0 ->
        pluralStringResource(R.plurals.reminder_days, minutes / 1_440, minutes / 1_440)
    minutes % 60 == 0 ->
        pluralStringResource(R.plurals.reminder_hours, minutes / 60, minutes / 60)
    else -> pluralStringResource(R.plurals.reminder_minutes, minutes, minutes)
}

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
        keyboardType = keyboardType,
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
) {
    val dateFormat = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    val timeFormat = remember(locale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    }
    // Tappable values read as links (primary), like the location on the
    // detail screen; errors flip them to the error colour.
    val valueColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
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
                .clickable(onClick = onDateClick)
                .padding(vertical = 8.dp, horizontal = 6.dp),
        )
        if (!isAllDay) {
            Text(
                text = timeFormat.format(dateTime.time.toJavaLocalTime()),
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
                modifier = Modifier
                    .clickable(onClick = onTimeClick)
                    .padding(vertical = 8.dp, horizontal = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerAlert(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // DatePicker speaks UTC-midnight millis; epoch-day arithmetic keeps the
    // conversion zone-proof in both directions.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toEpochDays() * MILLIS_PER_DAY,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onConfirm(LocalDate.fromEpochDays((millis / MILLIS_PER_DAY).toInt()))
                    }
                },
            ) { Text(stringResource(R.string.dialog_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerAlert(
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime(state.hour, state.minute)) }) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
        text = { TimePicker(state = state) },
    )
}

@Composable
private fun CalendarPickerDialog(
    calendars: List<CalendarSource>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.event_detail_calendar)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                calendars.forEach { calendar ->
                    OptionCard(
                        label = calendar.displayName,
                        onClick = { onSelect(calendar.id) },
                        icon = Icons.Default.CalendarMonth,
                        // The calendar's own colour carries its identity.
                        iconTint = pastelize(calendar.color, dark),
                        supportingText = calendar.accountName.takeIf { it.isNotBlank() },
                        selected = calendar.id == selectedId,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

private const val MILLIS_PER_DAY = 86_400_000L
