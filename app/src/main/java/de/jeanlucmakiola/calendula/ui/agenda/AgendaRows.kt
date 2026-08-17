package de.jeanlucmakiola.calendula.ui.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.common.EventDimAlpha
import de.jeanlucmakiola.calendula.ui.common.declinedTitle
import de.jeanlucmakiola.calendula.ui.common.LocalSoftenColors
import de.jeanlucmakiola.calendula.ui.common.LocalUse24HourFormat
import de.jeanlucmakiola.calendula.ui.common.eventAccent
import de.jeanlucmakiola.calendula.ui.common.eventFill
import de.jeanlucmakiola.calendula.ui.common.formatTimeOfDay
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.floret.locale.currentLocale
import de.jeanlucmakiola.floret.locale.localizedDateFormatter
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import java.util.Locale

// The agenda's row vocabulary, split out of AgendaScreen so the month view's
// split style can list a day with exactly the same visual language instead of
// growing a parallel set of event rows.

@Composable
internal fun AgendaDayHeader(
    date: LocalDate,
    today: LocalDate,
    onOpenDay: (LocalDate) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDay(date) },
    ) {
        Text(
            text = agendaDayLabel(date, today),
            style = MaterialTheme.typography.titleSmall,
            color = if (date == today) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 8.dp),
        )
    }
}

/**
 * A card standing in for a day with no events — the same coffee-cup motif as the
 * agenda's full-screen empty state, boxed into a card so the day keeps a visible slot
 * rather than a bare header. Used for an anchored, event-less today (#35) and for
 * an empty selected day in the month view's split style.
 */
@Composable
internal fun AgendaEmptyDayRow(text: String, onClick: () -> Unit) {
    Card(
        // Match a single event row's resting corner radius (floret groupedShape).
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Filled.Coffee,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun AgendaEventRow(
    event: EventInstance,
    day: LocalDate,
    zone: TimeZone,
    position: Position,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val soften = LocalSoftenColors.current
    val title = event.title.ifBlank { stringResource(R.string.event_untitled) }
    GroupedRow(
        modifier = if (dimmed) modifier.alpha(EventDimAlpha) else modifier,
        title = declinedTitle(title, event.isDeclined),
        summary = AnnotatedString(agendaTimeSummary(event, day, zone)),
        position = position,
        minHeight = 64.dp,
        leading = {
            Box(
                modifier = Modifier
                    .size(width = 6.dp, height = 36.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(eventAccent(event.color, dark, soften)),
            )
        },
        onClick = onClick,
    )
}

/** "Today · Wed, 17. Jun 2026" — relative word for today/tomorrow, else the date. */
@Composable
internal fun agendaDayLabel(date: LocalDate, today: LocalDate): String {
    val relative = when (date) {
        today -> stringResource(R.string.agenda_header_today)
        today.plus(1, DateTimeUnit.DAY) -> stringResource(R.string.agenda_header_tomorrow)
        else -> null
    }
    val formatted = formatAgendaDate(date)
    return if (relative != null) "$relative · $formatted" else formatted
}

/**
 * Time line under the title: "09:00 – 10:00 · Location", "All day", etc.
 *
 * A multi-day event shows only the part relevant to [day], spelled out so each
 * day reads on its own: its first day names the start ("Starts 14:00"), its last
 * day the end ("Ends 10:00"), and any whole day in between reads as "All day".
 * An all-day multi-day event is simply "All day" on every day it covers.
 */
@Composable
internal fun agendaTimeSummary(event: EventInstance, day: LocalDate, zone: TimeZone): String {
    val is24Hour = LocalUse24HourFormat.current
    val locale = currentLocale()

    val time = when (val label = agendaTimeLabel(event, day, zone)) {
        AgendaTimeLabel.AllDay -> stringResource(R.string.event_detail_all_day)
        is AgendaTimeLabel.Starts -> stringResource(
            R.string.agenda_span_starts,
            formatTime(label.start, zone, is24Hour, locale),
        )
        is AgendaTimeLabel.Ends -> stringResource(
            R.string.agenda_span_ends,
            formatTime(label.end, zone, is24Hour, locale),
        )
        is AgendaTimeLabel.Range -> "${formatTime(label.start, zone, is24Hour, locale)} – " +
            formatTime(label.end, zone, is24Hour, locale)
    }

    val location = event.location?.takeIf { it.isNotBlank() }
    return if (location != null) "$time · $location" else time
}

private fun formatTime(
    instant: Instant,
    zone: TimeZone,
    is24Hour: Boolean,
    locale: Locale,
): String {
    val t = instant.toLocalDateTime(zone).time
    return formatTimeOfDay(t.hour, t.minute, is24Hour, locale)
}

private fun formatAgendaDate(date: LocalDate): String {
    val locale = Locale.getDefault()
    val java = java.time.LocalDate.of(date.year, date.month.ordinal + 1, date.day)
    // Weekday + date in the locale's own field order (e.g. "Wed, Jun 17, 2026"
    // vs "Mi., 17. Juni 2026") rather than a hardcoded day-month-year layout.
    return localizedDateFormatter(locale, "EEEdMMMy").format(java)
}
