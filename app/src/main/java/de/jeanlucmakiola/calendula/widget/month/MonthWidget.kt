package de.jeanlucmakiola.calendula.widget.month

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
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
import de.jeanlucmakiola.calendula.ui.common.formatCalendarTitle
import de.jeanlucmakiola.calendula.ui.month.MonthWeek
import de.jeanlucmakiola.calendula.ui.month.layoutMonthWeeks
import de.jeanlucmakiola.calendula.ui.common.eventFill
import de.jeanlucmakiola.calendula.ui.common.eventInk
import de.jeanlucmakiola.calendula.widget.CalendulaGlanceTheme
import de.jeanlucmakiola.calendula.widget.MonthWidgetSource
import de.jeanlucmakiola.calendula.widget.WidgetSize
import de.jeanlucmakiola.calendula.widget.loadMonthWidgetSource
import de.jeanlucmakiola.calendula.widget.systemZone
import de.jeanlucmakiola.calendula.widget.today
import de.jeanlucmakiola.calendula.widget.widgetEntryPoint
import kotlinx.coroutines.flow.first
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

/**
 * Per-instance Glance state key holding the chosen [WidgetSize]. Read reactively
 * in the composition so changing the setting reflects on a live widget by plain
 * recomposition — `updateAll` does not reliably re-run the `provideGlance`
 * preamble for a live session (same reason as [MONTH_INDEX_KEY]).
 */
internal val MONTH_SIZE_KEY = stringPreferencesKey("widget_size")

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
 * Columns are a fixed [MonthMetrics.columnWidth] wide — the user's chosen
 * [WidgetSize], never a measured size — and the grid is centred in whatever space
 * the host gives it. Sizing the columns off the launcher-reported width is what
 * broke the grid (#103): the reported width overshot the width actually drawn
 * into, so `width / 7` came out too wide and only the first four columns fitted.
 * A fixed column also keeps a multi-day event one connected Box spanning its
 * columns — no inter-cell seam, rounded end caps — which weighted columns, the
 * other way to be measurement-free, could not express.
 *
 * The displayed month lives in Glance state and is read reactively in the
 * composition ([currentState]) so the arrows move it via plain recomposition, not
 * a (here-unreliable) widget session reload.
 */
class MonthWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    // Single, not Exact: nothing in the layout depends on the widget's measured
    // size any more (#103), so there is no reason to pay for one RemoteViews per
    // host size bucket.
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val source = context.loadMonthWidgetSource()
        val dark = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        // Read fresh (not through the cached source) so toggling the softener or
        // the size redraws with the new choice; two cheap DataStore reads.
        val prefs = context.widgetEntryPoint().settingsPrefs()
        val soften = prefs.softenCalendarColors.first()
        val savedSize = prefs.widgetSize.first()
        provideContent {
            CalendulaGlanceTheme {
                MonthWidgetBody(
                    source = source,
                    dark = dark,
                    soften = soften,
                    savedSize = savedSize,
                )
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
private fun MonthWidgetBody(
    source: MonthWidgetSource,
    dark: Boolean,
    soften: Boolean,
    savedSize: WidgetSize,
) {
    // Size read reactively from per-instance Glance state (falling back to the
    // saved pref for a freshly placed widget), never from the measured size.
    val metrics = monthMetricsFor(currentState(MONTH_SIZE_KEY).toWidgetSize(savedSize))
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(horizontal = metrics.gridPadding, vertical = 6.dp),
    ) {
        when (source) {
            MonthWidgetSource.NeedsPermission -> {
                MonthHeader(label = "Calendula", metrics = metrics)
                PermissionMessage()
            }
            is MonthWidgetSource.Ready -> {
                val zone = systemZone()
                val index = currentState(MONTH_INDEX_KEY) ?: currentMonthIndex(zone)
                val ym = yearMonthOf(index)
                val weeks = layoutMonthWeeks(ym, source.weekStart, source.instances, zone)

                MonthHeader(label = monthLabel(ym, source.today.year), metrics = metrics)
                Spacer(GlanceModifier.height(2.dp))
                WeekdayHeader(weekStart = source.weekStart, metrics = metrics)
                weeks.forEach { week ->
                    WeekRow(
                        week = week,
                        currentMonth = ym.month,
                        today = source.today,
                        dark = dark,
                        soften = soften,
                        metrics = metrics,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                }
            }
        }
    }
}

/**
 * A grid row: the seven fixed-width columns, centred in the space the host gave
 * us. Every row of the grid goes through this so they can never drift out of
 * alignment, and centring means leftover width shows as an even margin either
 * side rather than a ragged edge (turn the size up to fill it).
 */
@Composable
private fun GridRow(content: @Composable androidx.glance.layout.RowScope.() -> Unit) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

private fun String?.toWidgetSize(default: WidgetSize): WidgetSize =
    this?.let { stored -> WidgetSize.entries.firstOrNull { it.name == stored } } ?: default

@Composable
private fun MonthHeader(label: String, metrics: MonthMetrics) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderIcon(
            resId = R.drawable.ic_widget_chevron_left,
            contentDescription = context.getString(R.string.widget_prev_month),
            metrics = metrics,
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
                fontSize = metrics.headerTitle,
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
            metrics = metrics,
            onClick = GlanceModifier.clickable(actionRunCallback<ResetMonthAction>()),
        )
        HeaderIcon(
            resId = R.drawable.ic_widget_chevron_right,
            contentDescription = context.getString(R.string.widget_next_month),
            metrics = metrics,
            onClick = GlanceModifier.clickable(
                actionRunCallback<ShiftMonthAction>(
                    actionParametersOf(ShiftMonthAction.deltaKey to 1),
                ),
            ),
        )
    }
}

@Composable
private fun HeaderIcon(
    resId: Int,
    contentDescription: String,
    metrics: MonthMetrics,
    onClick: GlanceModifier,
) {
    Box(
        modifier = GlanceModifier.size(metrics.iconBox).then(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(resId),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            modifier = GlanceModifier.size(metrics.iconImage),
        )
    }
}

@Composable
private fun WeekdayHeader(weekStart: DayOfWeek, metrics: MonthMetrics) {
    GridRow {
        weekdayNarrowNames(weekStart).forEach { name ->
            Text(
                text = name,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = metrics.weekday,
                    textAlign = TextAlign.Center,
                ),
                modifier = GlanceModifier.width(metrics.columnWidth),
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
    soften: Boolean,
    metrics: MonthMetrics,
    modifier: GlanceModifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Day numbers.
        GridRow {
            week.days.forEach { date ->
                DayNumber(
                    date = date,
                    isToday = date == today,
                    inMonth = date.month == currentMonth,
                    metrics = metrics,
                )
            }
        }
        Spacer(GlanceModifier.height(2.dp))
        // One lane row per event row. A multi-day span is a single Box spanning
        // its columns (columnWidth * n) so it's connected with no seam and
        // rounded ends.
        repeat(MAX_LANES) { lane ->
            LaneRow(week = week, lane = lane, dark = dark, soften = soften, metrics = metrics)
            Spacer(GlanceModifier.height(1.dp))
        }
        OverflowRow(week = week, metrics = metrics)
    }
}

/**
 * Open [date]'s day view rooted in the month view (so back returns to the month
 * grid) — the same target the in-app month grid uses when a day cell is tapped.
 * Shared by every tappable part of a day column so, as in the app, a tap anywhere
 * on a day opens it; only an event bar on top opts out to open its own detail.
 */
private fun openDayAction(context: Context, date: LocalDate) =
    actionStartActivity(MainActivity.openDateIntent(context, date, CalendarView.Month))

@Composable
private fun DayNumber(date: LocalDate, isToday: Boolean, inMonth: Boolean, metrics: MonthMetrics) {
    val context = LocalContext.current
    val diameter = metrics.dayNumberHeight
    Box(
        modifier = GlanceModifier
            .width(metrics.columnWidth)
            .height(diameter)
            .clickable(openDayAction(context, date)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = GlanceModifier
                .size(diameter)
                .then(if (isToday) GlanceModifier.cornerRadius(diameter / 2).background(GlanceTheme.colors.primary) else GlanceModifier),
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
                    fontSize = metrics.dayNumber,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                ),
            )
        }
    }
}

@Composable
private fun LaneRow(week: MonthWeek, lane: Int, dark: Boolean, soften: Boolean, metrics: MonthMetrics) {
    val context = LocalContext.current
    GridRow {
        var col = 0
        while (col < 7) {
            val span = week.spans.firstOrNull { it.lane == lane && col in it.startCol..it.endCol }
            if (span != null) {
                val cols = span.endCol - col + 1
                SpanBar(
                    event = span.event,
                    dark = dark,
                    soften = soften,
                    width = metrics.columnWidth * cols,
                    metrics = metrics,
                )
                col = span.endCol + 1
            } else {
                val timed = timedEventAt(week, lane, col, week.days[col])
                if (timed != null) {
                    SpanBar(
                        event = timed,
                        dark = dark,
                        soften = soften,
                        width = metrics.columnWidth,
                        metrics = metrics,
                    )
                } else {
                    // Empty lane cell: a tap opens that day, so blank space in a
                    // day column is a day-open target just like the number is.
                    Box(
                        GlanceModifier
                            .width(metrics.columnWidth)
                            .height(metrics.laneHeight)
                            .clickable(openDayAction(context, week.days[col])),
                    ) {}
                }
                col += 1
            }
        }
    }
}

/** A single connected, rounded event bar [width] wide with its clipped title. */
@Composable
private fun SpanBar(
    event: EventInstance,
    dark: Boolean,
    soften: Boolean,
    width: Dp,
    metrics: MonthMetrics,
) {
    val context = LocalContext.current
    val fill = eventFill(event.color, dark, soften)
    Box(
        modifier = GlanceModifier
            .width(width)
            .height(metrics.laneHeight)
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
                .background(fill),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = event.title.ifBlank { context.getString(R.string.event_untitled) },
                maxLines = 1,
                style = TextStyle(color = ColorProvider(eventInk(fill)), fontSize = metrics.eventTitle),
                modifier = GlanceModifier.padding(horizontal = 3.dp),
            )
        }
    }
}

@Composable
private fun OverflowRow(week: MonthWeek, metrics: MonthMetrics) {
    val context = LocalContext.current
    GridRow {
        week.days.forEachIndexed { col, date ->
            val shownSpans = week.spans.count { col in it.startCol..it.endCol && it.lane < MAX_LANES }
            val freeSlots = (MAX_LANES - shownSpans).coerceAtLeast(0)
            val timedShown = minOf(freeSlots, week.timedByDay[date].orEmpty().size)
            val hidden = (week.countByDay[date] ?: 0) - shownSpans - timedShown
            // The overflow row is part of the day column too: tapping it (whether
            // it shows "+N" or is blank) opens that day, same as the app.
            Box(
                modifier = GlanceModifier
                    .width(metrics.columnWidth)
                    .height(metrics.laneHeight)
                    .clickable(openDayAction(context, date)),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (hidden > 0) {
                    Text(
                        text = "+$hidden",
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = metrics.overflow),
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

// Locale.getDefault() rather than currentLocale(): Glance has no
// LocalConfiguration, and the widget is re-rendered on a configuration change
// anyway (same convention as AgendaWidget).
private fun monthLabel(month: YearMonth, currentYear: Int): String =
    formatCalendarTitle(
        date = java.time.LocalDate.of(month.year, month.month.ordinal + 1, 1),
        locale = Locale.getDefault(),
        currentYear = currentYear,
        skeleton = "LLLL",
    )
