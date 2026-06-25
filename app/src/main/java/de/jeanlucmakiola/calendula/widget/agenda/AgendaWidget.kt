package de.jeanlucmakiola.calendula.widget.agenda

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.pastelize
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
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * "Upcoming" agenda widget — a continuously scrolling list of the next ~30 days
 * of events grouped under day headers (the Google "Schedule" widget model).
 * Reuses the app's [groupAgendaDays] grouping so it matches the in-app agenda.
 */
class AgendaWidget : GlanceAppWidget() {

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
            is AgendaWidgetData.Ready ->
                if (data.days.isEmpty()) {
                    WidgetMessage(R.string.agenda_empty_title)
                } else {
                    val rows = buildList {
                        data.days.forEach { day ->
                            add(AgendaRow.Header(day.date, data.today))
                            day.events.forEach { add(AgendaRow.Event(it)) }
                        }
                    }
                    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                        items(rows.size) { index ->
                            when (val row = rows[index]) {
                                is AgendaRow.Header -> DayHeaderRow(row.date, row.today)
                                is AgendaRow.Event -> EventRow(row.event, dark)
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
        Text(
            text = context.getString(R.string.widget_agenda_title),
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = GlanceModifier.defaultWeight(),
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

@Composable
private fun EventRow(event: EventInstance, dark: Boolean) {
    val context = androidx.glance.LocalContext.current
    val title = event.title.ifBlank { context.getString(R.string.event_untitled) }
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
                .background(pastelize(event.color, dark)),
        ) {}
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = title,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp),
            )
            Text(
                text = eventTimeSummary(context, event),
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
    val weekday = java.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale)
    val monthName = java.month.getDisplayName(JavaTextStyle.SHORT, locale)
    val formatted = "$weekday, ${date.day} $monthName"
    return if (relative != null) "$relative · $formatted" else formatted
}

private fun eventTimeSummary(context: Context, event: EventInstance): String {
    val time = if (event.isAllDay) {
        context.getString(R.string.event_detail_all_day)
    } else {
        "${formatTime(event.start)} – ${formatTime(event.end)}"
    }
    val location = event.location?.takeIf { it.isNotBlank() }
    return if (location != null) "$time · $location" else time
}

private fun formatTime(instant: Instant): String {
    val t = instant.toLocalDateTime(zone()).time
    return "%02d:%02d".format(t.hour, t.minute)
}
