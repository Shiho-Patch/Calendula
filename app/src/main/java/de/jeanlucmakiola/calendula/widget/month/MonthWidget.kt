package de.jeanlucmakiola.calendula.widget.month

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.currentState
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
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import de.jeanlucmakiola.calendula.MainActivity
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.pastelize
import de.jeanlucmakiola.calendula.ui.month.MonthWeek
import de.jeanlucmakiola.calendula.ui.month.layoutMonthWeeks
import de.jeanlucmakiola.calendula.widget.CalendulaGlanceTheme
import de.jeanlucmakiola.calendula.widget.MonthWidgetSource
import de.jeanlucmakiola.calendula.widget.loadMonthWidgetSource
import de.jeanlucmakiola.calendula.widget.systemZone
import de.jeanlucmakiola.calendula.widget.today
import androidx.compose.ui.unit.Dp
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/** Per-widget state: the displayed month as `year * 12 + monthOrdinal`. */
private val MONTH_INDEX_KEY = intPreferencesKey("month_index")

/** Event rows (lanes) shown per week before the rest collapse into "+N". */
private const val MAX_LANES = 3
private val LANE_HEIGHT = 14.dp
private val DAY_NUMBER_HEIGHT = 18.dp
private val GRID_HPADDING = 8.dp

/** Dark ink that reads on the pastelized event fills, like the in-app MonthBar. */
private val EventInk = ColorProvider(Color(0xDE000000))

private fun currentMonthIndex(zone: TimeZone): Int {
    val t = today(zone)
    return t.year * 12 + t.month.ordinal
}

private fun yearMonthOf(index: Int): YearMonth =
    YearMonth(index / 12, Month(index % 12 + 1))

/**
 * Month-grid widget: a 6×7 calendar with today highlighted, connected multi-day
 * event bars and titled single-day pills (the in-app lane layout via
 * [layoutMonthWeeks]), and prev/next/today navigation.
 *
 * Columns are sized explicitly from [LocalSize] (hence [SizeMode.Exact]) so a
 * multi-day span renders as a single Box spanning its columns — connected, no
 * inter-cell seam, with rounded end caps. The displayed month lives in Glance
 * state and is read reactively in the composition ([currentState]) so the arrows
 * move it via plain recomposition, not a (here-unreliable) widget session reload.
 */
class MonthWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val source = context.loadMonthWidgetSource()
        val dark = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        provideContent {
            CalendulaGlanceTheme {
                MonthWidgetBody(source = source, dark = dark)
            }
        }
    }
}

/** Step the displayed month by the `delta` action parameter (±1). */
class ShiftMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[deltaKey] ?: 0
        updateAppWidgetState(context, glanceId) { prefs ->
            val cur = prefs[MONTH_INDEX_KEY] ?: currentMonthIndex(systemZone())
            prefs[MONTH_INDEX_KEY] = cur + delta
        }
        MonthWidget().updateAll(context.applicationContext)
    }

    companion object {
        val deltaKey = ActionParameters.Key<Int>("delta")
    }
}

/** Jump the displayed month back to the current month. */
class ResetMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { prefs -> prefs.remove(MONTH_INDEX_KEY) }
        MonthWidget().updateAll(context.applicationContext)
    }
}

@Composable
private fun MonthWidgetBody(source: MonthWidgetSource, dark: Boolean) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(horizontal = GRID_HPADDING, vertical = 6.dp),
    ) {
        when (source) {
            MonthWidgetSource.NeedsPermission -> {
                MonthHeader(label = "Calendula")
                PermissionMessage()
            }
            is MonthWidgetSource.Ready -> {
                val zone = systemZone()
                val index = currentState(MONTH_INDEX_KEY) ?: currentMonthIndex(zone)
                val ym = yearMonthOf(index)
                // Column width from the live widget size, minus our H padding.
                val colW = (LocalSize.current.width - GRID_HPADDING * 2) / 7
                val weeks = layoutMonthWeeks(ym, source.weekStart, source.instances, zone)

                MonthHeader(label = monthLabel(ym))
                Spacer(GlanceModifier.height(2.dp))
                WeekdayHeader(weekStart = source.weekStart, colW = colW)
                weeks.forEach { week ->
                    WeekRow(
                        week = week,
                        currentMonth = ym.month,
                        today = source.today,
                        dark = dark,
                        colW = colW,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(label: String) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderIcon(
            resId = R.drawable.ic_widget_chevron_left,
            contentDescription = context.getString(R.string.widget_prev_month),
            onClick = GlanceModifier.clickable(
                actionRunCallback<ShiftMonthAction>(
                    actionParametersOf(ShiftMonthAction.deltaKey to -1),
                ),
            ),
        )
        // Tap the month/year title to open the app on the month view (issue #18),
        // rooted over the default home so backing out returns there.
        Text(
            text = label,
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(
                    actionStartActivity(
                        MainActivity.openViewIntent(context, CalendarView.Month),
                    ),
                ),
        )
        // The "today" button snaps the grid back to the current month in place.
        HeaderIcon(
            resId = R.drawable.ic_widget_today,
            contentDescription = context.getString(R.string.widget_today),
            onClick = GlanceModifier.clickable(actionRunCallback<ResetMonthAction>()),
        )
        HeaderIcon(
            resId = R.drawable.ic_widget_chevron_right,
            contentDescription = context.getString(R.string.widget_next_month),
            onClick = GlanceModifier.clickable(
                actionRunCallback<ShiftMonthAction>(
                    actionParametersOf(ShiftMonthAction.deltaKey to 1),
                ),
            ),
        )
    }
}

@Composable
private fun HeaderIcon(resId: Int, contentDescription: String, onClick: GlanceModifier) {
    Box(
        modifier = GlanceModifier.size(40.dp).then(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(resId),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier.size(20.dp),
        )
    }
}

@Composable
private fun WeekdayHeader(weekStart: DayOfWeek, colW: Dp) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        weekdayNarrowNames(weekStart).forEach { name ->
            Text(
                text = name,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                ),
                modifier = GlanceModifier.width(colW),
            )
        }
    }
}

/** Narrow weekday initials starting at [weekStart], in the device locale.
 * Computed outside the composable so the locale read stays observable-safe. */
private fun weekdayNarrowNames(weekStart: DayOfWeek): List<String> {
    val locale = Locale.getDefault()
    return (0 until 7).map { i ->
        java.time.DayOfWeek.of((weekStart.ordinal + i) % 7 + 1)
            .getDisplayName(JavaTextStyle.NARROW, locale)
    }
}

@Composable
private fun WeekRow(
    week: MonthWeek,
    currentMonth: Month,
    today: LocalDate,
    dark: Boolean,
    colW: Dp,
    modifier: GlanceModifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Day numbers.
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            week.days.forEach { date ->
                DayNumber(
                    date = date,
                    isToday = date == today,
                    inMonth = date.month == currentMonth,
                    colW = colW,
                )
            }
        }
        Spacer(GlanceModifier.height(2.dp))
        // One lane row per event row. A multi-day span is a single Box spanning
        // its columns (colW * n) so it's connected with no seam and rounded ends.
        repeat(MAX_LANES) { lane ->
            LaneRow(week = week, lane = lane, dark = dark, colW = colW)
            Spacer(GlanceModifier.height(1.dp))
        }
        OverflowRow(week = week, colW = colW)
    }
}

@Composable
private fun DayNumber(date: LocalDate, isToday: Boolean, inMonth: Boolean, colW: Dp) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .width(colW)
            .height(DAY_NUMBER_HEIGHT)
            // Tap a day number to open that day, rooted in the month view so back
            // returns to the month grid.
            .clickable(
                actionStartActivity(MainActivity.openDateIntent(context, date, CalendarView.Month)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = GlanceModifier
                .size(DAY_NUMBER_HEIGHT)
                .then(if (isToday) GlanceModifier.cornerRadius(DAY_NUMBER_HEIGHT / 2).background(GlanceTheme.colors.primary) else GlanceModifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.day.toString(),
                style = TextStyle(
                    color = when {
                        isToday -> GlanceTheme.colors.onPrimary
                        inMonth -> GlanceTheme.colors.onSurface
                        else -> GlanceTheme.colors.onSurfaceVariant
                    },
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                ),
            )
        }
    }
}

@Composable
private fun LaneRow(week: MonthWeek, lane: Int, dark: Boolean, colW: Dp) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        var col = 0
        while (col < 7) {
            val span = week.spans.firstOrNull { it.lane == lane && col in it.startCol..it.endCol }
            if (span != null) {
                val cols = span.endCol - col + 1
                SpanBar(event = span.event, dark = dark, width = colW * cols)
                col = span.endCol + 1
            } else {
                val timed = timedEventAt(week, lane, col, week.days[col])
                if (timed != null) {
                    SpanBar(event = timed, dark = dark, width = colW)
                } else {
                    Box(GlanceModifier.width(colW).height(LANE_HEIGHT)) {}
                }
                col += 1
            }
        }
    }
}

/** A single connected, rounded event bar [width] wide with its clipped title. */
@Composable
private fun SpanBar(event: EventInstance, dark: Boolean, width: Dp) {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier
            .width(width)
            .height(LANE_HEIGHT)
            .padding(horizontal = 1.dp)
            // Tap an event bar to open its detail, rooted in the month view.
            .clickable(
                actionStartActivity(
                    MainActivity.openEventIntent(
                        context = context,
                        eventId = event.eventId,
                        beginMillis = event.start.toEpochMilliseconds(),
                        endMillis = event.end.toEpochMilliseconds(),
                        source = CalendarView.Month,
                    ),
                ),
            ),
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(4.dp)
                .background(pastelize(event.color, dark)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = event.title.ifBlank { context.getString(R.string.event_untitled) },
                maxLines = 1,
                style = TextStyle(color = EventInk, fontSize = 9.sp),
                modifier = GlanceModifier.padding(horizontal = 3.dp),
            )
        }
    }
}

@Composable
private fun OverflowRow(week: MonthWeek, colW: Dp) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        week.days.forEachIndexed { col, date ->
            val shownSpans = week.spans.count { col in it.startCol..it.endCol && it.lane < MAX_LANES }
            val freeSlots = (MAX_LANES - shownSpans).coerceAtLeast(0)
            val timedShown = minOf(freeSlots, week.timedByDay[date].orEmpty().size)
            val hidden = (week.countByDay[date] ?: 0) - shownSpans - timedShown
            Box(
                modifier = GlanceModifier.width(colW).height(LANE_HEIGHT),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (hidden > 0) {
                    Text(
                        text = "+$hidden",
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp),
                        modifier = GlanceModifier.padding(start = 3.dp),
                    )
                }
            }
        }
    }
}

/** The timed single-day event that fills lane [lane] on day [col], if any. */
private fun timedEventAt(week: MonthWeek, lane: Int, col: Int, date: LocalDate): EventInstance? {
    val occupied = week.spans
        .filter { col in it.startCol..it.endCol && it.lane < MAX_LANES }
        .map { it.lane }
        .toSet()
    val freeSlots = (0 until MAX_LANES).filter { it !in occupied }
    val timed = week.timedByDay[date].orEmpty()
    return timed.getOrNull(freeSlots.indexOf(lane))
}

@Composable
private fun PermissionMessage() {
    val context = LocalContext.current
    Box(
        modifier = GlanceModifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = context.getString(R.string.widget_needs_permission),
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

private fun monthLabel(month: YearMonth): String {
    val locale = Locale.getDefault()
    val name = java.time.Month.of(month.month.ordinal + 1)
        .getDisplayName(JavaTextStyle.FULL, locale)
    return "$name ${month.year}"
}
