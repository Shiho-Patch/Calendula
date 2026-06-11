package de.jeanlucmakiola.calendula.ui.edit

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventFormProblem
import de.jeanlucmakiola.calendula.ui.common.currentLocale
import de.jeanlucmakiola.calendula.ui.common.pastelize
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Clock

/**
 * Full-screen event form (v1.2: create only). Opens prefilled from the FAB's
 * anchor date; Save validates, writes via the repository, and closes. The
 * calendar picker offers only writable calendars.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(
    initialDateIso: String?,
    onClose: () -> Unit,
    viewModel: EventEditViewModel = hiltViewModel(),
) {
    LaunchedEffect(initialDateIso) {
        val date = initialDateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        viewModel.openNew(date)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The form is intentionally forgotten on every close (cancel or save) so
    // the next FAB tap starts clean; it survives rotation because openNew
    // no-ops while a form is set.
    val close = {
        viewModel.reset()
        onClose()
    }
    BackHandler(onBack = close)

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
            SaveUiState.Saved -> close()
            SaveUiState.Failed -> {
                viewModel.consumeSaveResult()
                snackbarHostState.showSnackbar(saveFailedMessage)
            }
            SaveUiState.NeedsPermission -> {
                viewModel.consumeSaveResult()
                snackbarHostState.showSnackbar(writeDeniedMessage)
            }
            SaveUiState.Idle, SaveUiState.Saving, null -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.event_edit_new_title)) },
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
}

private enum class PickerTarget { StartDate, StartTime, EndDate, EndTime }

@Composable
private fun EventEditContent(
    state: EventEditUiState,
    viewModel: EventEditViewModel,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    val locale = currentLocale()
    var picker by remember { mutableStateOf<PickerTarget?>(null) }
    var showCalendarPicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 40.dp),
    ) {
        // Title: a large borderless field, MD3 "create" idiom.
        TextField(
            value = form.title,
            onValueChange = viewModel::setTitle,
            placeholder = {
                Text(
                    text = stringResource(R.string.event_edit_title_hint),
                    style = MaterialTheme.typography.headlineSmall,
                )
            },
            textStyle = MaterialTheme.typography.headlineSmall,
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // All-day toggle.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.event_detail_all_day),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = form.isAllDay, onCheckedChange = viewModel::setAllDay)
        }

        Spacer(Modifier.height(8.dp))

        // Start / end rows: tappable date + (for timed events) time labels.
        DateTimeRow(
            label = stringResource(R.string.event_edit_starts),
            dateTime = form.start,
            isAllDay = form.isAllDay,
            locale = locale,
            onDateClick = { picker = PickerTarget.StartDate },
            onTimeClick = { picker = PickerTarget.StartTime },
        )
        DateTimeRow(
            label = stringResource(R.string.event_edit_ends),
            dateTime = form.end,
            isAllDay = form.isAllDay,
            locale = locale,
            onDateClick = { picker = PickerTarget.EndDate },
            onTimeClick = { picker = PickerTarget.EndTime },
            isError = EventFormProblem.EndBeforeStart in state.problems,
        )
        if (EventFormProblem.EndBeforeStart in state.problems) {
            Text(
                text = stringResource(R.string.event_edit_error_end_before_start),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 40.dp, top = 2.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // Calendar picker row.
        val selectedCalendar = state.calendars.firstOrNull { it.id == form.calendarId }
        val dark = isSystemInDarkTheme()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = state.calendars.isNotEmpty()) { showCalendarPicker = true }
                .padding(vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = stringResource(R.string.event_detail_calendar),
                tint = selectedCalendar?.let { pastelize(it.color, dark) }
                    ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedCalendar?.displayName
                        ?: stringResource(R.string.event_edit_error_no_calendar),
                    style = MaterialTheme.typography.bodyLarge,
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
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = form.location,
            onValueChange = viewModel::setLocation,
            label = { Text(stringResource(R.string.event_detail_location)) },
            leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = form.description,
            onValueChange = viewModel::setDescription,
            label = { Text(stringResource(R.string.event_detail_description)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
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
}

/** One schedule row: label, then tappable date and (unless all-day) time. */
@Composable
private fun DateTimeRow(
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
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = dateFormat.format(dateTime.date.toJavaLocalDate()),
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            modifier = Modifier
                .clickable(onClick = onDateClick)
                .padding(8.dp),
        )
        if (!isAllDay) {
            Text(
                text = timeFormat.format(dateTime.time.toJavaLocalTime()),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
                modifier = Modifier
                    .clickable(onClick = onTimeClick)
                    .padding(8.dp),
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
            Column {
                calendars.forEach { calendar ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = calendar.id == selectedId,
                                role = Role.RadioButton,
                                onClick = { onSelect(calendar.id) },
                            )
                            .padding(vertical = 8.dp),
                    ) {
                        RadioButton(selected = calendar.id == selectedId, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = pastelize(calendar.color, dark),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(calendar.displayName, style = MaterialTheme.typography.bodyLarge)
                            if (calendar.accountName.isNotBlank()) {
                                Text(
                                    text = calendar.accountName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

private const val MILLIS_PER_DAY = 86_400_000L
