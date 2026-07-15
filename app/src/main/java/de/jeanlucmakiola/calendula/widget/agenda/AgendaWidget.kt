package de.jeanlucmakiola.calendula.widget.agenda

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import de.jeanlucmakiola.calendula.MainActivity
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
import de.jeanlucmakiola.calendula.data.prefs.parsePastEventDisplay
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.hasEnded
import de.jeanlucmakiola.calendula.ui.agenda.AgendaRange
import de.jeanlucmakiola.calendula.ui.agenda.anchorTodayIfMissing
import de.jeanlucmakiola.calendula.ui.agenda.dayCount
import de.jeanlucmakiola.calendula.ui.agenda.parseAgendaRange
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.EventDimAlpha
import de.jeanlucmakiola.calendula.ui.common.formatTimeOfDay
import de.jeanlucmakiola.calendula.ui.common.localizedDateFormatter
import de.jeanlucmakiola.floret.components.pastelize
import de.jeanlucmakiola.calendula.widget.AgendaWidgetData
import de.jeanlucmakiola.calendula.widget.CalendulaGlanceTheme
import de.jeanlucmakiola.calendula.widget.loadAgendaWidgetData
import de.jeanlucmakiola.calendula.widget.systemZone
import de.jeanlucmakiola.calendula.widget.today
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import java.util.Locale

/**
 * "Upcoming" agenda widget — a continuously scrolling list of the next ~30 days
 * of events grouped under day headers (the Google "Schedule" widget model).
 * Reuses the app's [groupAgendaDays] grouping so it matches the in-app agenda.
 */
/**
 * Per-instance Glance state key holding the agenda range (as [AgendaRange.storageValue]).
 * The range is read reactively in the composition ([currentState]) so a settings
 * change is reflected by plain recomposition — `updateAll` does NOT reliably
 * re-run the `provideGlance` preamble for a live widget session, so loading the
 * range there and slicing it would silently keep the stale value (mirrors the
 * month widget's MONTH_INDEX_KEY approach).
 */
internal val AGENDA_RANGE_KEY = stringPreferencesKey("agenda_range")

/**
 * Per-instance Glance state key holding the past-event display mode (as
 * [PastEventDisplay.name]). Read reactively in the composition for the same
 * reason as [AGENDA_RANGE_KEY] — so toggling the setting reflects on the live
 * widget without depending on the `provideGlance` preamble re-running.
 */
internal val AGENDA_PAST_DISPLAY_KEY = stringPreferencesKey("agenda_past_display")

/**
 * Per-instance Glance state key holding the "always show today" toggle (#35).
 * Read reactively in the composition for the same reason as [AGENDA_RANGE_KEY] —
 * so toggling the setting reflects on the live widget without depending on the
 * `provideGlance` preamble re-running.
 */
internal val AGENDA_SHOW_TODAY_STATE_KEY = booleanPreferencesKey("agenda_show_today")

class AgendaWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = context.loadAgendaWidgetData()
        val dark = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        provideContent {
            CalendulaGlanceTheme {
                AgendaWidgetBody(data = data, dark = dark)
            }
        }
    }
}

/** Re-reads the calendar and redraws the widget (header refresh button). */
class RefreshAgendaAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        AgendaWidget().updateAll(context.applicationContext)
    }
}

/** Flat row model so the [LazyColumn] can mix day headers and events. */
private sealed interface AgendaRow {
    data class Header(val date: LocalDate, val today: LocalDate) : AgendaRow
    data class Event(val event: EventInstance) : AgendaRow
    /** "Nothing left today" line under an anchored, event-less today (#35). */
    data class Placeholder(val date: LocalDate) : AgendaRow
}

@Composable
private fun AgendaWidgetBody(data: AgendaWidgetData, dark: Boolean) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        AgendaHeader()
        Spacer(GlanceModifier.height(4.dp))
        when (data) {
            AgendaWidgetData.NeedsPermission -> WidgetMessage(R.string.widget_needs_permission)
            is AgendaWidgetData.Ready -> {
                // Range read reactively from per-instance Glance state (falls back
                // to the saved pref for a freshly placed widget), then the wide
                // pre-loaded window is sliced to it — pure recomposition.
                val range = parseAgendaRange(currentState(AGENDA_RANGE_KEY), data.savedRange)
                val pastDisplay =
                    parsePastEventDisplay(currentState(AGENDA_PAST_DISPLAY_KEY), data.savedPastDisplay)
                val showToday = currentState(AGENDA_SHOW_TODAY_STATE_KEY) ?: data.savedShowToday
                val rangeEnd = data.today.plus(
                    range.dayCount(data.today, data.weekStart) - 1,
                    DateTimeUnit.DAY,
                )
                // Slice to the chosen range, then drop finished events (and any day
                // they leave empty) when the mode is Hide — so "Upcoming" really is.
                // Finally re-anchor today (as an empty placeholder day) when the
                // "always show today" pref is on and today has no events left (#35).
                val visibleDays = anchorTodayIfMissing(
                    days = data.days
                        .filter { it.date <= rangeEnd }
                        .let { days ->
                            if (pastDisplay == PastEventDisplay.HIDE) {
                                days.mapNotNull { day ->
                                    val remaining = day.events.filterNot { it.hasEnded(data.now) }
                                    if (remaining.isEmpty()) null else day.copy(events = remaining)
                                }
                            } else {
                                days
                            }
                        },
                    today = data.today,
                    enabled = showToday,
                )
                if (visibleDays.isEmpty()) {
                    WidgetMessage(R.string.agenda_empty_title)
                } else {
                    val rows = buildList {
                        visibleDays.forEach { day ->
                            add(AgendaRow.Header(day.date, data.today))
                            if (day.events.isEmpty()) {
                                add(AgendaRow.Placeholder(day.date))
                            } else {
                                day.events.forEach { add(AgendaRow.Event(it)) }
                            }
                        }
                    }
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(rows.size) { index ->
                            when (val row = rows[index]) {
                                is AgendaRow.Header -> DayHeaderRow(row.date, row.today)
                                is AgendaRow.Placeholder -> PlaceholderRow(row.date)
                                is AgendaRow.Event -> EventRow(
                                    event = row.event,
                                    dark = dark,
                                    is24Hour = data.is24Hour,
                                    dimmed = pastDisplay == PastEventDisplay.DIM &&
                                        row.event.hasEnded(data.now),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgendaHeader() {
    val context = androidx.glance.LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tap the "Upcoming" title to open the app on the user's default view
        // (issue #20) — a null target resolves to the default home in the host.
        Text(
            text = context.getString(R.string.widget_agenda_title),
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(
                    actionStartActivity(MainActivity.openViewIntent(context, view = null)),
                ),
        )
        IconButton(
            resId = R.drawable.ic_widget_refresh,
            contentDescription = context.getString(R.string.widget_refresh),
            onClick = GlanceModifier.clickable(actionRunCallback<RefreshAgendaAction>()),
        )
        IconButton(
            resId = R.drawable.ic_widget_add,
            contentDescription = context.getString(R.string.widget_new_event),
            onClick = GlanceModifier.clickable(
                actionStartActivity(
                    MainActivity.openCreateIntent(context, today(systemZone())),
                ),
            ),
        )
    }
}

@Composable
private fun IconButton(resId: Int, contentDescription: String, onClick: GlanceModifier) {
    Box(
        modifier = GlanceModifier.size(40.dp).then(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(resId),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier.size(22.dp),
        )
    }
}

@Composable
private fun DayHeaderRow(date: LocalDate, today: LocalDate) {
    val context = androidx.glance.LocalContext.current
    Text(
        text = agendaDayLabel(context, date, today),
        style = TextStyle(
            color = if (date == today) GlanceTheme.colors.primary
            else GlanceTheme.colors.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        ),
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 4.dp)
            .clickable(
                actionStartActivity(
                    MainActivity.openDateIntent(context, date, CalendarView.Agenda),
                ),
            ),
    )
}

/**
 * "No more events today" under an anchored, event-less today (#35). Kept plain —
 * a muted line indented to the event titles — since the widget's event rows are
 * plain too (a stripe + text, not cards), so a card here would look out of place.
 */
@Composable
private fun PlaceholderRow(date: LocalDate) {
    val context = androidx.glance.LocalContext.current
    Text(
        text = context.getString(R.string.agenda_no_more_today),
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(start = 19.dp, end = 8.dp, top = 2.dp, bottom = 6.dp)
            .clickable(
                actionStartActivity(
                    MainActivity.openDateIntent(context, date, CalendarView.Agenda),
                ),
            ),
    )
}

@Composable
private fun EventRow(event: EventInstance, dark: Boolean, is24Hour: Boolean, dimmed: Boolean) {
    val context = androidx.glance.LocalContext.current
    val title = event.title.ifBlank { context.getString(R.string.event_untitled) }
    // Glance has no generic alpha modifier, so dim by fading the colour stripe and
    // dropping both text lines to the lower-emphasis on-surface-variant tone.
    val stripeColor = pastelize(event.color, dark).let {
        if (dimmed) it.copy(alpha = EventDimAlpha) else it
    }
    val titleColor = if (dimmed) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .clickable(
                actionStartActivity(
                    MainActivity.openEventIntent(
                        context = context,
                        eventId = event.eventId,
                        beginMillis = event.start.toEpochMilliseconds(),
                        endMillis = event.end.toEpochMilliseconds(),
                        source = CalendarView.Agenda,
                    ),
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .width(5.dp)
                .height(36.dp)
                .cornerRadius(3.dp)
                .background(stripeColor),
        ) {}
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = title,
                maxLines = 1,
                style = TextStyle(color = titleColor, fontSize = 14.sp),
            )
            Text(
                text = eventTimeSummary(context, event, is24Hour),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun WidgetMessage(resId: Int) {
    val context = androidx.glance.LocalContext.current
    Box(
        modifier = GlanceModifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = context.getString(resId),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp),
        )
    }
}

private fun zone(): TimeZone = systemZone()

/** "Today · Wed, 17 Jun" — relative word for today/tomorrow, else the date. */
private fun agendaDayLabel(context: Context, date: LocalDate, today: LocalDate): String {
    val relative = when (date) {
        today -> context.getString(R.string.agenda_header_today)
        today.plus(1, DateTimeUnit.DAY) -> context.getString(R.string.agenda_header_tomorrow)
        else -> null
    }
    val locale = Locale.getDefault()
    val java = java.time.LocalDate.of(date.year, date.month.ordinal + 1, date.day)
    // Weekday + date in the locale's own field order (compact: no year), rather
    // than a hardcoded day-month layout.
    val formatted = localizedDateFormatter(locale, "EEEdMMM").format(java)
    return if (relative != null) "$relative · $formatted" else formatted
}

private fun eventTimeSummary(context: Context, event: EventInstance, is24Hour: Boolean): String {
    val time = if (event.isAllDay) {
        context.getString(R.string.event_detail_all_day)
    } else {
        "${formatTime(event.start, is24Hour)} – ${formatTime(event.end, is24Hour)}"
    }
    val location = event.location?.takeIf { it.isNotBlank() }
    return if (location != null) "$time · $location" else time
}

private fun formatTime(instant: Instant, is24Hour: Boolean): String {
    val t = instant.toLocalDateTime(zone()).time
    return formatTimeOfDay(t.hour, t.minute, is24Hour, Locale.getDefault())
}
