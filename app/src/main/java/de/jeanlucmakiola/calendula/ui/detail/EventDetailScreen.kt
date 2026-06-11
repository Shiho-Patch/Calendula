package de.jeanlucmakiola.calendula.ui.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.icu.text.ListFormatter
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
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
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.EventStatus
import de.jeanlucmakiola.calendula.domain.Reminder
import de.jeanlucmakiola.calendula.ui.common.CalendarFailure
import de.jeanlucmakiola.calendula.ui.common.currentLocale
import de.jeanlucmakiola.calendula.ui.common.pastelize
import kotlinx.datetime.TimeZone
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Read-only full-screen event detail (spec S4, realised as a navigation
 * destination rather than a bottom sheet — MD3 list→detail pattern). Back
 * gesture and the top-bar arrow both return to the calendar. The only action is
 * tapping the location to open a maps intent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    eventId: Long,
    beginMillis: Long,
    endMillis: Long,
    onBack: () -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(eventId, beginMillis, endMillis) {
        viewModel.open(eventId, beginMillis, endMillis)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    Scaffold(
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
                    IconButton(onClick = { /* TODO: edit event (V2) */ }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.event_detail_edit),
                        )
                    }
                    IconButton(onClick = { /* TODO: delete event (V2) */ }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.event_detail_delete),
                        )
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
}

@Composable
private fun EventDetailContent(state: EventDetailUiState.Success, modifier: Modifier = Modifier) {
    val detail = state.detail
    val instance = detail.instance
    val dark = isSystemInDarkTheme()
    val locale = currentDetailLocale()
    val accent = pastelize(instance.color, dark)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 40.dp),
    ) {
        // Title with a short accent line in the calendar colour underneath.
        // A cancelled event strikes through the title.
        Text(
            text = instance.title.ifBlank { stringResource(R.string.event_untitled) },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textDecoration = if (detail.status == EventStatus.Cancelled) {
                TextDecoration.LineThrough
            } else {
                null
            },
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .width(48.dp)
                .height(3.dp)
                .background(accent, RoundedCornerShape(2.dp)),
        )

        // Status / availability / access chips. Availability is always known, so
        // this row always shows at least the Free/Busy chip.
        Spacer(Modifier.height(16.dp))
        StatusChips(detail.status, detail.availability, detail.accessLevel)

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
        // than the device's, so cross-zone events read unambiguously.
        foreignTimeZoneLabel(detail.eventTimezone, instance.isAllDay, locale)?.let { tzLabel ->
            Spacer(Modifier.height(gap))
            DetailCard(
                icon = Icons.Default.Public,
                iconContentDescription = stringResource(R.string.event_detail_timezone),
            ) {
                Text(text = tzLabel, style = MaterialTheme.typography.titleMedium)
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
            Text(
                text = attendee.name.ifBlank { attendee.email.orEmpty() },
                style = MaterialTheme.typography.bodyMedium,
            )
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

/** Status / availability / access pills shown directly under the title accent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusChips(
    status: EventStatus,
    availability: Availability,
    accessLevel: AccessLevel,
) {
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

        val availabilityLabel = if (availability == Availability.Free) {
            R.string.event_availability_free
        } else {
            R.string.event_availability_busy
        }
        InfoChip(text = stringResource(availabilityLabel))

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
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(color = container, shape = RoundedCornerShape(8.dp)) {
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
private fun reminderLeadText(reminder: Reminder): String {
    val minutes = reminder.minutes
    return when {
        minutes < 0 -> stringResource(R.string.reminder_default)
        minutes == 0 -> stringResource(R.string.reminder_at_time)
        minutes % 10_080 == 0 -> {
            val weeks = minutes / 10_080
            pluralStringResource(R.plurals.reminder_weeks, weeks, weeks)
        }
        minutes % 1_440 == 0 -> {
            val days = minutes / 1_440
            pluralStringResource(R.plurals.reminder_days, days, days)
        }
        minutes % 60 == 0 -> {
            val hours = minutes / 60
            pluralStringResource(R.plurals.reminder_hours, hours, hours)
        }
        else -> pluralStringResource(R.plurals.reminder_minutes, minutes, minutes)
    }
}

/**
 * A localized label for [tz] (e.g. "Central European Time (Europe/Berlin)"),
 * but only when the event is timed and pinned to a zone different from the
 * device's. Returns null when there's nothing worth showing.
 */
private fun foreignTimeZoneLabel(tz: String?, isAllDay: Boolean, locale: Locale): String? {
    if (isAllDay || tz.isNullOrBlank()) return null
    val deviceZone = ZoneId.systemDefault().id
    if (tz == deviceZone) return null
    return try {
        val zone = ZoneId.of(tz)
        val name = zone.getDisplayName(JavaTextStyle.FULL, locale)
        if (name == tz) tz else "$name ($tz)"
    } catch (e: Exception) {
        tz
    }
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
 * Humanise an RFC 5545 RRULE into a localized phrase, e.g.
 * "Every week on Tue and Thu until 31 Dec 2026" or "Every day, 10 times".
 * Falls back to a generic label for rules we don't render in full (ordinal
 * monthly/yearly BYDAY, etc.).
 */
@Composable
private fun recurrenceText(rrule: String, locale: Locale): AnnotatedString {
    val parts = rrule.removePrefix("RRULE:").split(';').mapNotNull { token ->
        val eq = token.indexOf('=')
        if (eq <= 0) null else token.substring(0, eq).uppercase() to token.substring(eq + 1)
    }.toMap()

    val freq = parts["FREQ"]?.uppercase()
    val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
    val base = when (freq) {
        "DAILY" -> if (interval == 1) stringResource(R.string.recurrence_daily)
        else stringResource(R.string.recurrence_every_n_days, interval)
        "WEEKLY" -> if (interval == 1) stringResource(R.string.recurrence_weekly)
        else stringResource(R.string.recurrence_every_n_weeks, interval)
        "MONTHLY" -> if (interval == 1) stringResource(R.string.recurrence_monthly)
        else stringResource(R.string.recurrence_every_n_months, interval)
        "YEARLY" -> if (interval == 1) stringResource(R.string.recurrence_yearly)
        else stringResource(R.string.recurrence_every_n_years, interval)
        else -> return AnnotatedString(stringResource(R.string.event_detail_recurring))
    }

    // Weekly + BYDAY → "<base> on <days>"; other BYDAY forms keep just the base.
    // The day names + their joined block are tracked so only the names (not the
    // commas/conjunction) can be italicised in the final string.
    val byDay = parts["BYDAY"]
    var dayNames: List<String>? = null
    var joinedDays: String? = null
    val main = if (freq == "WEEKLY" && !byDay.isNullOrBlank()) {
        val days = byDay.split(',').mapNotNull { rruleDayName(it.trim(), locale) }
        if (days.isNotEmpty()) {
            val joined = ListFormatter.getInstance(locale).format(days)
            dayNames = days
            joinedDays = joined
            stringResource(R.string.recurrence_on_days, base, joined)
        } else {
            base
        }
    } else {
        base
    }

    // End bound: UNTIL (a date) takes precedence over COUNT (a number of times).
    val until = parts["UNTIL"]?.let { parseUntilDate(it, locale) }
    val count = parts["COUNT"]?.toIntOrNull()
    val full = when {
        until != null -> stringResource(R.string.recurrence_with_until, main, until)
        count != null -> stringResource(R.string.recurrence_with_count, main, count)
        else -> main
    }

    return buildAnnotatedString {
        append(full)
        val names = dayNames
        val joined = joinedDays
        if (names != null && joined != null) {
            // Italicise each day name within the joined block only — leaving the
            // separators and conjunction ("und"/"and") in the regular style.
            val regionStart = full.indexOf(joined)
            if (regionStart >= 0) {
                val regionEnd = regionStart + joined.length
                var cursor = regionStart
                for (name in names) {
                    val at = full.indexOf(name, cursor)
                    if (at in regionStart until regionEnd) {
                        addStyle(SpanStyle(fontStyle = FontStyle.Italic), at, at + name.length)
                        cursor = at + name.length
                    }
                }
            }
        }
    }
}

/** Map an RRULE BYDAY token (e.g. "TU" or "2TH") to a localized short weekday name. */
private fun rruleDayName(token: String, locale: Locale): String? {
    val dow = when (token.takeLast(2).uppercase()) {
        "MO" -> DayOfWeek.MONDAY
        "TU" -> DayOfWeek.TUESDAY
        "WE" -> DayOfWeek.WEDNESDAY
        "TH" -> DayOfWeek.THURSDAY
        "FR" -> DayOfWeek.FRIDAY
        "SA" -> DayOfWeek.SATURDAY
        "SU" -> DayOfWeek.SUNDAY
        else -> return null
    }
    return dow.getDisplayName(JavaTextStyle.SHORT, locale)
}

/** Parse an RRULE UNTIL value ("20261231" or "20261231T235959Z") to a localized date. */
private fun parseUntilDate(raw: String, locale: Locale): String? {
    val digits = raw.takeWhile { it.isDigit() }
    if (digits.length < 8) return null
    return try {
        val date = java.time.LocalDate.of(
            digits.substring(0, 4).toInt(),
            digits.substring(4, 6).toInt(),
            digits.substring(6, 8).toInt(),
        )
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
    } catch (e: Exception) {
        null
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
    val timeShort = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)

    val startLdt = instance.start.toJavaLocalDateTime(zid)
    val allDayLabel = stringResource(R.string.event_detail_all_day)

    if (instance.isAllDay) {
        // All-day end is the exclusive next midnight; step back to the last
        // covered day so a one-day event reads as a single date.
        val lastLdt = (instance.end - 1.seconds).toJavaLocalDateTime(zid)
        return if (startLdt.toLocalDate() == lastLdt.toLocalDate()) {
            allDayLabel to dateFull.format(startLdt.toLocalDate())
        } else {
            allDayLabel to
                "${dateMedium.format(startLdt.toLocalDate())} – ${dateMedium.format(lastLdt.toLocalDate())}"
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
