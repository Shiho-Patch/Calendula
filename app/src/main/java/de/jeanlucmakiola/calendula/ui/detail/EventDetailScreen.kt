package de.jeanlucmakiola.calendula.ui.detail

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.AccessLevel
import de.jeanlucmakiola.calendula.domain.Attendee
import de.jeanlucmakiola.calendula.domain.AttendeeRelationship
import de.jeanlucmakiola.calendula.domain.AttendeeStatus
import de.jeanlucmakiola.calendula.domain.AttendeeType
import de.jeanlucmakiola.calendula.domain.Availability
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.EventStatus
import de.jeanlucmakiola.calendula.domain.RecurringWriteScope
import de.jeanlucmakiola.calendula.domain.Reminder
import de.jeanlucmakiola.calendula.domain.TimeZoneOption
import de.jeanlucmakiola.calendula.domain.timeZoneOptionOf
import de.jeanlucmakiola.calendula.domain.zoneDescriptor
import de.jeanlucmakiola.floret.identity.predictiveBack
import de.jeanlucmakiola.calendula.ui.common.CalendarFailure
import de.jeanlucmakiola.calendula.ui.common.icuTimeZoneRegion
import de.jeanlucmakiola.calendula.ui.common.LocalSoftenColors
import de.jeanlucmakiola.calendula.ui.common.eventAccent
import de.jeanlucmakiola.calendula.ui.common.eventFill
import de.jeanlucmakiola.floret.components.OptionCard
import de.jeanlucmakiola.floret.locale.currentLocale
import de.jeanlucmakiola.calendula.ui.common.LocalUse24HourFormat
import de.jeanlucmakiola.calendula.ui.common.timeOfDayFormatter
import de.jeanlucmakiola.calendula.ui.common.recurrenceText
import de.jeanlucmakiola.calendula.ui.common.reminderLeadTimeLabel
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlin.time.toJavaInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Full-screen event detail (spec S4, realised as a navigation destination
 * rather than a bottom sheet — MD3 list→detail pattern). Back gesture and the
 * top-bar arrow both return to the calendar. Events in writable calendars can
 * be deleted (v1.1) and edited (v1.3) from here; [onEdit] opens the shared
 * event form for this occurrence — for recurring events the form asks how
 * far the change reaches when saving. [onDuplicate] opens the same form
 * seeded with a copy of this event as a new, unsaved event (#52) — offered
 * for any event, including read-only ones, since the copy lands in an
 * editable calendar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    eventId: Long,
    beginMillis: Long,
    endMillis: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: (EventForm) -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(eventId, beginMillis, endMillis) {
        viewModel.open(eventId, beginMillis, endMillis)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleteState by viewModel.deleteState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    // Sharing is read-only, so it needs no WRITE_CALENDAR upgrade. The VM stages
    // an .ics in the cache and hands back a content Uri for the chooser.
    val shareFailedMessage = stringResource(R.string.event_share_failed)
    val shareChooserTitle = stringResource(R.string.event_share_chooser_title)
    val onShareClick = {
        scope.launch {
            val uri = viewModel.shareUri()
            val sent = uri != null && runCatching {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/calendar"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, shareChooserTitle))
            }.isSuccess
            if (!sent) snackbarHostState.showSnackbar(shareFailedMessage)
        }
        Unit
    }

    // v1.0 installs only hold READ_CALENDAR; the first write asks for the
    // upgrade in place. Granting continues straight into the tapped action —
    // edit, delete, or duplicate — held here until the result comes back.
    var pendingWrite by remember { mutableStateOf<(() -> Unit)?>(null) }
    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) pendingWrite?.invoke()
        pendingWrite = null
    }
    val hasWritePermission = {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
    }
    val requireWrite: (() -> Unit) -> Unit = { action ->
        if (hasWritePermission()) {
            action()
        } else {
            pendingWrite = action
            writePermissionLauncher.launch(Manifest.permission.WRITE_CALENDAR)
        }
    }
    val onDeleteClick = { requireWrite { showDeleteDialog = true } }
    val onEditClick = { requireWrite(onEdit) }
    // Duplicate reads the loaded detail into a create form and hands it up; the
    // create still needs WRITE_CALENDAR even when the source is read-only.
    val onDuplicateClick = {
        requireWrite { viewModel.duplicateForm()?.let(onDuplicate) }
    }

    val deleteFailedMessage = stringResource(R.string.event_delete_failed)
    val writeDeniedMessage = stringResource(R.string.event_delete_write_denied)
    LaunchedEffect(deleteState) {
        when (deleteState) {
            DeleteUiState.Deleted -> {
                viewModel.consumeDeleteResult()
                onBack()
            }
            DeleteUiState.Failed -> {
                viewModel.consumeDeleteResult()
                snackbarHostState.showSnackbar(deleteFailedMessage)
            }
            DeleteUiState.NeedsPermission -> {
                viewModel.consumeDeleteResult()
                snackbarHostState.showSnackbar(writeDeniedMessage)
            }
            DeleteUiState.Idle, DeleteUiState.Deleting -> Unit
        }
    }

    Scaffold(
        modifier = Modifier.predictiveBack(onBack = onBack),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.event_detail_back),
                        )
                    }
                },
                actions = {
                    val s = state
                    // Share and duplicate work for any loaded event — both only
                    // read it; the duplicate is created into a writable calendar.
                    if (s is EventDetailUiState.Success) {
                        IconButton(onClick = onShareClick) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.event_detail_share),
                            )
                        }
                        IconButton(
                            onClick = onDuplicateClick,
                            enabled = deleteState != DeleteUiState.Deleting,
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.event_detail_duplicate),
                            )
                        }
                    }
                    // Edit/delete need a writable calendar — WebCal subscriptions,
                    // birthday calendars etc. are read-only at the provider level.
                    if (s is EventDetailUiState.Success && s.canModify) {
                        IconButton(
                            onClick = onEditClick,
                            enabled = deleteState != DeleteUiState.Deleting,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.event_detail_edit),
                            )
                        }
                        // Managed special-dates events have no delete — the sync
                        // would resurrect them; the date is removed at the contact.
                        if (!s.isManaged) {
                            IconButton(
                                onClick = onDeleteClick,
                                enabled = deleteState != DeleteUiState.Deleting,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.event_detail_delete),
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        when (val s = state) {
            EventDetailUiState.Loading -> EventDetailLoading(contentModifier)
            is EventDetailUiState.Failure -> CalendarFailure(
                reason = s.reason,
                onRetry = viewModel::retry,
            )
            is EventDetailUiState.Success -> EventDetailContent(s, contentModifier)
        }
    }

    val loaded = state
    if (showDeleteDialog && loaded is EventDetailUiState.Success) {
        DeleteEventDialog(
            isRecurring = !loaded.detail.rrule.isNullOrBlank(),
            onConfirm = { scope ->
                showDeleteDialog = false
                viewModel.delete(scope)
            },
            onDismiss = { showDeleteDialog = false },
        )
    }
}

/**
 * Delete confirmation. Recurring events choose between cancelling just the
 * tapped occurrence (default), truncating the series from it onwards, and
 * removing the whole series.
 */
@Composable
private fun DeleteEventDialog(
    isRecurring: Boolean,
    onConfirm: (RecurringWriteScope) -> Unit,
    onDismiss: () -> Unit,
) {
    var scope by rememberSaveable { mutableStateOf(RecurringWriteScope.ThisEvent) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (isRecurring) R.string.event_delete_recurring_title
                    else R.string.event_delete_title,
                ),
            )
        },
        text = {
            if (isRecurring) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OptionCard(
                        label = stringResource(R.string.event_delete_option_occurrence),
                        onClick = { scope = RecurringWriteScope.ThisEvent },
                        selected = scope == RecurringWriteScope.ThisEvent,
                    )
                    OptionCard(
                        label = stringResource(R.string.event_delete_option_following),
                        onClick = { scope = RecurringWriteScope.ThisAndFollowing },
                        selected = scope == RecurringWriteScope.ThisAndFollowing,
                    )
                    OptionCard(
                        label = stringResource(R.string.event_delete_option_series),
                        onClick = { scope = RecurringWriteScope.AllEvents },
                        selected = scope == RecurringWriteScope.AllEvents,
                    )
                }
            } else {
                Text(stringResource(R.string.event_delete_body))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(if (isRecurring) scope else RecurringWriteScope.AllEvents) },
            ) {
                Text(
                    text = stringResource(R.string.event_detail_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        },
    )
}


@Composable
private fun EventDetailContent(state: EventDetailUiState.Success, modifier: Modifier = Modifier) {
    val detail = state.detail
    val instance = detail.instance
    val dark = isSystemInDarkTheme()
    val locale = currentDetailLocale()
    val accent = eventAccent(instance.color, dark, LocalSoftenColors.current)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 40.dp),
    ) {
        // Title row: title on the left, a "Free" pill pinned top-right when the
        // event doesn't block your time. Busy is the default for nearly every
        // event, so it's left implicit — only Free is worth surfacing. A
        // cancelled event strikes through its title.
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = instance.title.ifBlank { stringResource(R.string.event_untitled) },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (detail.status == EventStatus.Cancelled) {
                    TextDecoration.LineThrough
                } else {
                    null
                },
                modifier = Modifier.weight(1f),
            )
            if (detail.availability == Availability.Free) {
                Spacer(Modifier.width(12.dp))
                InfoChip(
                    text = stringResource(R.string.event_availability_free),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(3.dp)
                .background(accent, RoundedCornerShape(2.dp)),
        )

        // Status / access chips — shown only when noteworthy (Confirmed status
        // and Default/Public access are the silent norm).
        val hasStatusChips = detail.status != EventStatus.Confirmed ||
            detail.accessLevel == AccessLevel.Private ||
            detail.accessLevel == AccessLevel.Confidential
        if (hasStatusChips) {
            Spacer(Modifier.height(16.dp))
            StatusChips(detail.status, detail.accessLevel)
        }

        Spacer(Modifier.height(20.dp))

        // Every piece of info shares one card design: a tonal container with a
        // leading icon in the gutter and the value to the right. 12dp gaps stack
        // them cleanly.
        val gap = 12.dp

        // "When" — date/all-day plus the time range.
        val (whenPrimary, whenSecondary) = formatWhen(instance, TimeZone.currentSystemDefault(), locale)
        DetailCard(icon = Icons.Default.Schedule, iconContentDescription = null) {
            Text(text = whenPrimary, style = MaterialTheme.typography.titleMedium)
            if (whenSecondary != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = whenSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Time zone — only when the event is timed and pinned to a zone other
        // than the device's, so cross-zone events read unambiguously. It answers
        // "when was it set", which the zone's name alone never did: an 8 AM New
        // York call showing as 2 PM here should still say 8 AM somewhere.
        //
        // Same hierarchy as the When card above — the label carries the card,
        // the time sits small beneath it. The local time is the one the reader
        // acts on, so the original must stay quieter than it, not compete.
        val foreignZone = remember(detail.eventTimezone, instance.isAllDay, instance.start, locale) {
            foreignTimeZone(detail.eventTimezone, instance.isAllDay, locale, instance.start)
        }
        foreignZone?.let { zoneOption ->
            Spacer(Modifier.height(gap))
            DetailCard(
                icon = Icons.Default.Public,
                iconContentDescription = stringResource(R.string.event_detail_timezone),
            ) {
                // The id is the primary label ("Europe/Berlin"); the long
                // localized name spelled next to it made the field too wide.
                Text(text = zoneOption.label, style = MaterialTheme.typography.titleMedium)
                // Beneath, small: the abbreviation plus the event's own-zone time,
                // e.g. "CET · 8:00 AM – 9:00 AM". formatWhen puts the range in the
                // secondary half only for a same-day event; one spanning midnight
                // — which a cross-zone event easily does — carries the whole span
                // in the primary instead, so fall back to it. If the zone can't be
                // read back at all, drop to just the abbreviation + offset.
                val originalTime = runCatching { TimeZone.of(zoneOption.id) }.getOrNull()
                    ?.let { formatWhen(instance, it, locale) }
                    ?.let { (primary, secondary) -> secondary ?: primary }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (originalTime != null) {
                        "${zoneOption.shortName} · $originalTime"
                    } else {
                        zoneDescriptor(zoneOption)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Calendar — icon tinted in the calendar colour conveys identity, so no
        // separate colour dot is needed.
        Spacer(Modifier.height(gap))
        DetailCard(
            icon = Icons.Default.CalendarMonth,
            iconTint = accent,
            iconContentDescription = stringResource(R.string.event_detail_calendar),
        ) {
            Text(
                text = state.calendarName ?: stringResource(R.string.event_detail_calendar_unknown),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        // Location (conditional, tap → maps).
        instance.location?.takeIf { it.isNotBlank() }?.let { location ->
            val context = LocalContext.current
            Spacer(Modifier.height(gap))
            DetailCard(
                icon = Icons.Default.Place,
                iconContentDescription = stringResource(R.string.event_detail_location),
            ) {
                Text(
                    text = location,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openInMaps(context, location) }
                        .padding(vertical = 2.dp),
                )
            }
        }

        // Description (conditional). URLs are auto-linked.
        detail.description?.takeIf { it.isNotBlank() }?.let { description ->
            Spacer(Modifier.height(gap))
            DetailCard(
                icon = Icons.AutoMirrored.Filled.Notes,
                iconContentDescription = stringResource(R.string.event_detail_description),
            ) {
                Text(
                    text = linkifyUrls(description, MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Attendees (conditional). The user's own response leads the list, then
        // each attendee with their role and reply.
        detail.attendees.takeIf { it.isNotEmpty() }?.let { attendees ->
            Spacer(Modifier.height(gap))
            DetailCard(
                icon = Icons.Default.People,
                iconContentDescription = stringResource(R.string.event_detail_attendees),
            ) {
                if (detail.selfStatus != AttendeeStatus.Unknown) {
                    Text(
                        text = stringResource(
                            R.string.event_detail_self_response,
                            stringResource(attendeeStatusLabel(detail.selfStatus)),
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                attendees.forEach { AttendeeRow(it) }
            }
        }

        // Reminders (conditional) — list each lead time before the event.
        detail.reminders.takeIf { it.isNotEmpty() }?.let { reminders ->
            Spacer(Modifier.height(gap))
            DetailCard(
                icon = Icons.Default.Notifications,
                iconContentDescription = stringResource(R.string.event_detail_reminders),
            ) {
                reminders
                    .distinctBy { it.minutes }
                    .sortedBy { it.minutes }
                    .forEach { reminder ->
                        Text(
                            text = reminderLeadText(reminder),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
            }
        }

        // Recurrence (conditional) — humanised from the RRULE.
        detail.rrule?.takeIf { it.isNotBlank() }?.let { rrule ->
            Spacer(Modifier.height(gap))
            DetailCard(
                icon = Icons.Default.Repeat,
                iconContentDescription = stringResource(R.string.event_detail_recurrence),
            ) {
                Text(
                    text = recurrenceText(rrule, locale),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/** One info card: tonal container, leading icon in the gutter, value to the right. */
@Composable
private fun DetailCard(
    icon: ImageVector,
    iconContentDescription: String?,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), content = content)
        }
    }
}

@Composable
private fun AttendeeRow(attendee: Attendee) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val hasName = attendee.name.isNotBlank()
            Text(
                text = if (hasName) attendee.name else attendee.email.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
            )
            // Email as supporting text — only when the name is the headline, so we
            // don't repeat it on rows that already fall back to the email above.
            if (hasName) {
                attendee.email?.takeIf { it.isNotBlank() }?.let { email ->
                    Text(
                        text = email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            attendeeRoleLabel(attendee)?.let { roleRes ->
                Text(
                    text = stringResource(roleRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(attendeeStatusLabel(attendee.status)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Status / access pills shown directly under the title accent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusChips(status: EventStatus, accessLevel: AccessLevel) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (status) {
            EventStatus.Cancelled -> InfoChip(
                text = stringResource(R.string.event_status_cancelled),
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            )
            EventStatus.Tentative -> InfoChip(
                text = stringResource(R.string.event_status_tentative),
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            EventStatus.Confirmed -> Unit
        }

        when (accessLevel) {
            AccessLevel.Private -> InfoChip(text = stringResource(R.string.event_access_private))
            AccessLevel.Confidential ->
                InfoChip(text = stringResource(R.string.event_access_confidential))
            AccessLevel.Default, AccessLevel.Public -> Unit
        }
    }
}

@Composable
private fun InfoChip(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(color = container, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun EventDetailLoading(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
        SkeletonBar(widthFraction = 0.7f, height = 32.dp)
        Spacer(Modifier.height(24.dp))
        SkeletonBar(widthFraction = 1f, height = 64.dp)
        Spacer(Modifier.height(28.dp))
        SkeletonBar(widthFraction = 0.35f, height = 14.dp)
        Spacer(Modifier.height(8.dp))
        SkeletonBar(widthFraction = 0.6f, height = 16.dp)
        Spacer(Modifier.height(24.dp))
        SkeletonBar(widthFraction = 0.35f, height = 14.dp)
        Spacer(Modifier.height(8.dp))
        SkeletonBar(widthFraction = 0.8f, height = 16.dp)
    }
}

@Composable
private fun SkeletonBar(widthFraction: Float, height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHighest,
                RoundedCornerShape(8.dp),
            ),
    )
}

// --- helpers -------------------------------------------------------------

// Observable locale read (shared helper) — avoids NonObservableLocale /
// LocalContextConfigurationRead lint by going through LocalConfiguration.
@Composable
private fun currentDetailLocale(): Locale = currentLocale()

private fun attendeeStatusLabel(status: AttendeeStatus): Int = when (status) {
    AttendeeStatus.Accepted -> R.string.event_attendee_accepted
    AttendeeStatus.Declined -> R.string.event_attendee_declined
    AttendeeStatus.Tentative -> R.string.event_attendee_tentative
    AttendeeStatus.NeedsAction -> R.string.event_attendee_needs_action
    AttendeeStatus.Unknown -> R.string.event_attendee_unknown
}

/**
 * The role badge shown under an attendee's name. Organizer wins over type;
 * required attendees (the common case) get no badge to keep the list quiet.
 */
private fun attendeeRoleLabel(attendee: Attendee): Int? = when {
    attendee.relationship == AttendeeRelationship.Organizer -> R.string.event_attendee_organizer
    attendee.type == AttendeeType.Optional -> R.string.event_attendee_optional
    attendee.type == AttendeeType.Resource -> R.string.event_attendee_resource
    else -> null
}

/** Humanise a reminder's lead time, e.g. "10 minutes before" / "At time of event". */
@Composable
private fun reminderLeadText(reminder: Reminder): String = reminderLeadTimeLabel(reminder.minutes)

/**
 * [tz] resolved as a [TimeZoneOption], but only when the event is timed and
 * pinned to a zone different from the device's — the cases where showing it
 * removes ambiguity. Null otherwise (all-day, device-zone, blank, or an id the
 * tz database doesn't know).
 *
 * Resolved *at [at]*, the event's own start, not at "now": the abbreviation and
 * offset both move with DST, and the card prints them next to the event's time.
 * Resolving at now would label a July event "CET · 10:00 AM" when read in
 * January — an abbreviation that contradicts the time beside it.
 */
private fun foreignTimeZone(
    tz: String?,
    isAllDay: Boolean,
    locale: Locale,
    at: Instant,
): TimeZoneOption? {
    if (isAllDay || tz.isNullOrBlank()) return null
    if (tz == ZoneId.systemDefault().id) return null
    return timeZoneOptionOf(
        tz,
        locale,
        at = at.toJavaInstant(),
        regionOf = ::icuTimeZoneRegion,
    )
}

/** Wrap http(s) URLs in [text] as tappable links tinted [linkColor]. */
@Composable
private fun linkifyUrls(text: String, linkColor: Color): AnnotatedString = remember(text, linkColor) {
    val regex = Regex("""https?://\S+""")
    val styles = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
    )
    buildAnnotatedString {
        append(text)
        for (match in regex.findAll(text)) {
            // Trim trailing punctuation that commonly abuts a URL in prose.
            val raw = match.value
            val url = raw.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '"', '\'')
            val end = match.range.first + url.length
            addLink(LinkAnnotation.Url(url, styles), match.range.first, end)
        }
    }
}

/**
 * Format an event's time into a primary line (date, or "All day") and an
 * optional secondary line (time range). Multi-day timed events collapse into a
 * single primary line spanning both ends.
 */
@Composable
private fun formatWhen(
    instance: EventInstance,
    zone: TimeZone,
    locale: Locale,
): Pair<String, String?> {
    val zid = ZoneId.of(zone.id)
    val dateFull = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    val dateMedium = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val timeShort = timeOfDayFormatter(LocalUse24HourFormat.current, locale)

    val startLdt = instance.start.toJavaLocalDateTime(zid)
    val allDayLabel = stringResource(R.string.event_detail_all_day)

    if (instance.isAllDay) {
        // All-day events live at UTC midnights with an exclusive end. Resolve
        // the covered dates in UTC — not the device zone, which would shift the
        // midnight boundaries off the intended date (east of UTC pushes the
        // end past the last day; west of UTC pulls the start back) — and step
        // the end back to the last covered day so a one-day event reads as a
        // single date.
        val utc = ZoneId.of("UTC")
        val startDate = instance.start.toJavaLocalDateTime(utc).toLocalDate()
        val lastDate = (instance.end - 1.seconds).toJavaLocalDateTime(utc).toLocalDate()
        return if (startDate == lastDate) {
            allDayLabel to dateFull.format(startDate)
        } else {
            allDayLabel to "${dateMedium.format(startDate)} – ${dateMedium.format(lastDate)}"
        }
    }

    val endLdt = instance.end.toJavaLocalDateTime(zid)
    return if (startLdt.toLocalDate() == endLdt.toLocalDate()) {
        dateFull.format(startLdt.toLocalDate()) to
            "${timeShort.format(startLdt)} – ${timeShort.format(endLdt)}"
    } else {
        val start = "${dateMedium.format(startLdt.toLocalDate())} ${timeShort.format(startLdt)}"
        val end = "${dateMedium.format(endLdt.toLocalDate())} ${timeShort.format(endLdt)}"
        "$start – $end" to null
    }
}

private fun Instant.toJavaLocalDateTime(zid: ZoneId): java.time.LocalDateTime =
    java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(toEpochMilliseconds()), zid)

/** Open a maps intent for [query]; fall back to a web maps URL if no app handles geo:. */
private fun openInMaps(context: Context, query: String) {
    val encoded = Uri.encode(query)
    val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded"))
    try {
        context.startActivity(geo)
    } catch (e: ActivityNotFoundException) {
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded"),
        )
        try {
            context.startActivity(web)
        } catch (e2: ActivityNotFoundException) {
            // No browser either — nothing sensible to do; swallow.
        }
    }
}
