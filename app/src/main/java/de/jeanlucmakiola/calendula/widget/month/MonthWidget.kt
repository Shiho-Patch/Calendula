package de.jeanlucmakiola.calendula.widget.month

import de.jeanlucmakiola.calendula.widget.glanceDeclinedDecoration
import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
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
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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

/** Event rows (lanes) shown per week before the rest collapse into "+N". */
private const val MAX_LANES = 3
private val LANE_HEIGHT = 14.dp
private val DAY_NUMBER_HEIGHT = 18.dp
private val GRID_HPADDING = 8.dp

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
 *
 * Everything here is written to keep the published `RemoteViews` small. A widget
 * update reaches the launcher as a *oneway* binder call, and the host's async
 * buffer is 1 MB for all of it; overrun throws `TransactionTooLargeException` and
 * the system drops the whole host, freezing every widget on the home screen until
 * the launcher rebinds. [SizeMode.Exact] serialises the grid once per size the
 * host reports, so the ceiling arrives at half the view count you would expect: a
 * grid that emitted a view per cell blew past it after three taps (#214). Every
 * row here emits only what it fills.
 */
class MonthWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    /**
     * Exact rather than [SizeMode.Single] or [SizeMode.Responsive], both of which
     * return early from `GlanceAppWidget.resize` — under those the grid is never
     * recomposed for a new size and a resized widget clips instead of reflowing.
     * Exact costs one serialised copy per host size, which is affordable now that
     * a rendering is ~190 views rather than ~740.
     */
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val source = context.loadMonthWidgetSource()
        val dark = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        // Read fresh (not through the cached source) so toggling the softener
        // redraws with the new choice; it's one cheap DataStore read.
        val soften = context.widgetEntryPoint().settingsPrefs().softenCalendarColors.first()
        provideContent {
            CalendulaGlanceTheme {
                MonthWidgetBody(source = source, dark = dark, soften = soften)
            }
        }
    }
}

/**
 * Step the displayed month by the `delta` action parameter (±1).
 *
 * Redrawn through [GlanceAppWidget.update] with the id the callback was handed,
 * never `updateAll`. `updateAll` resolves its targets through the provider-name
 * -> app-widget-id map Glance persists for the *receiver*, and in a process that
 * has done nothing else yet — the one a tap wakes after a reboot — that lookup
 * comes back empty and the redraw is dropped: the state write lands, nothing
 * recomposes, and the arrows read as dead until something else opens the app and
 * starts a session (#18). The tapped widget's own id needs no lookup.
 */
class ShiftMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta = parameters[deltaKey] ?: 0
        updateAppWidgetState(context, glanceId) { prefs ->
            val cur = prefs[MONTH_INDEX_KEY] ?: currentMonthIndex(systemZone())
            val next = cur + delta
            // Landing back on the current month clears the key rather than
            // storing today's index, so the widget goes back to *following* the
            // date instead of being pinned to the month that happened to be
            // current when it was tapped. Paging forward and back is the very
            // workaround #228's reporter used to force a redraw; storing the
            // index there would have left them stuck on that month for good once
            // it stopped being the current one.
            if (next == currentMonthIndex(systemZone())) {
                prefs.remove(MONTH_INDEX_KEY)
            } else {
                prefs[MONTH_INDEX_KEY] = next
            }
        }
        MonthWidget().update(context.applicationContext, glanceId)
    }

    companion object {
        val deltaKey = ActionParameters.Key<Int>("delta")
    }
}

/** Jump the displayed month back to the current month. See [ShiftMonthAction]. */
class ResetMonthAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        updateAppWidgetState(context, glanceId) { prefs -> prefs.remove(MONTH_INDEX_KEY) }
        MonthWidget().update(context.applicationContext, glanceId)
    }
}

@Composable
private fun MonthWidgetBody(source: MonthWidgetSource, dark: Boolean, soften: Boolean) {
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

                MonthHeader(label = monthLabel(ym, source.today.year))
                Spacer(GlanceModifier.height(2.dp))
                WeekdayHeader(weekStart = source.weekStart, colW = colW)
                weeks.forEach { week ->
                    WeekRow(
                        week = week,
                        currentMonth = ym.month,
                        today = source.today,
                        dark = dark,
                        soften = soften,
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
    soften: Boolean,
    colW: Dp,
    modifier: GlanceModifier,
) {
    val context = LocalContext.current
    val hidden = week.hiddenPerDay()
    Box(modifier = modifier.fillMaxWidth()) {
        // Day-open targets, one full-height strip per column, sitting *under* the
        // content: a tap anywhere in a day still opens it, as in the app, without
        // every cell in the column carrying a click target of its own. The content
        // above is not clickable, so touches fall through to this layer; only an
        // event bar opts out to open its own detail.
        Row(modifier = GlanceModifier.fillMaxSize()) {
            week.days.forEach { date ->
                Box(
                    modifier = GlanceModifier
                        .width(colW)
                        .fillMaxHeight()
                        .clickable(openDayAction(context, date)),
                ) {}
            }
        }
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            // Day numbers. Fixed height so every week's number row lines up whether
            // or not it holds today's larger pill.
            Row(
                modifier = GlanceModifier.fillMaxWidth().height(DAY_NUMBER_HEIGHT),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
            // Lanes past the last one this week fills emit nothing at all.
            repeat(week.usedLanes()) { lane ->
                LaneRow(week = week, lane = lane, dark = dark, soften = soften, colW = colW)
                Spacer(GlanceModifier.height(1.dp))
            }
            if (hidden.any { it > 0 }) OverflowRow(hidden = hidden, colW = colW)
        }
    }
}

/**
 * How many lanes this week actually fills, counting from the top. The grid draws
 * that many rows instead of a fixed [MAX_LANES]; an empty lane costs three views
 * per day and most weeks leave the last two empty.
 */
private fun MonthWeek.usedLanes(): Int {
    for (lane in MAX_LANES - 1 downTo 0) {
        val filled = spans.any { it.lane == lane } ||
            days.indices.any { col -> timedEventAt(this, lane, col, days[col]) != null }
        if (filled) return lane + 1
    }
    return 0
}

/** Events per day that no lane had room for — the "+N" counts, 0 where all fit. */
private fun MonthWeek.hiddenPerDay(): List<Int> = days.mapIndexed { col, date ->
    val shownSpans = spans.count { col in it.startCol..it.endCol && it.lane < MAX_LANES }
    val freeSlots = (MAX_LANES - shownSpans).coerceAtLeast(0)
    val timedShown = minOf(freeSlots, timedByDay[date].orEmpty().size)
    ((countByDay[date] ?: 0) - shownSpans - timedShown).coerceAtLeast(0)
}

/**
 * Open [date]'s day view rooted in the month view (so back returns to the month
 * grid) — the same target the in-app month grid uses when a day cell is tapped.
 * Shared by every tappable part of a day column so, as in the app, a tap anywhere
 * on a day opens it; only an event bar on top opts out to open its own detail.
 */
private fun openDayAction(context: Context, date: LocalDate) =
    actionStartActivity(MainActivity.openDateIntent(context, date, CalendarView.Month))

/**
 * A day's number. Every day but today is a bare centred [Text] carrying the column
 * width itself — a wrapping Box costs three views, and there are 42 of these.
 * Today keeps its filled circle, which does need the Box.
 */
@Composable
private fun DayNumber(date: LocalDate, isToday: Boolean, inMonth: Boolean, colW: Dp) {
    val style = TextStyle(
        color = when {
            isToday -> GlanceTheme.colors.onPrimary
            inMonth -> GlanceTheme.colors.onSurface
            else -> GlanceTheme.colors.onSurfaceVariant
        },
        fontSize = 11.sp,
        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
    )
    if (!isToday) {
        Text(text = date.day.toString(), style = style, modifier = GlanceModifier.width(colW))
        return
    }
    Box(modifier = GlanceModifier.width(colW), contentAlignment = Alignment.Center) {
        Box(
            modifier = GlanceModifier
                .size(DAY_NUMBER_HEIGHT)
                .cornerRadius(DAY_NUMBER_HEIGHT / 2)
                .background(GlanceTheme.colors.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = date.day.toString(), style = style)
        }
    }
}

/** A bar occupying [cols] columns, or — with a null event — that many blank ones. */
private data class LaneCell(val event: EventInstance?, val cols: Int)

/**
 * A lane split into bars and the gaps between them, with consecutive blank columns
 * merged into one gap: the week's day-open taps come from the strip underneath, so
 * a gap needs no per-column view and a mostly-empty lane collapses to a spacer.
 */
private fun MonthWeek.laneCells(lane: Int): List<LaneCell> {
    val cells = mutableListOf<LaneCell>()
    var gap = 0
    var col = 0
    fun closeGap() {
        if (gap > 0) cells += LaneCell(null, gap)
        gap = 0
    }
    while (col < 7) {
        val span = spans.firstOrNull { it.lane == lane && col in it.startCol..it.endCol }
        val timed = if (span == null) timedEventAt(this, lane, col, days[col]) else null
        when {
            span != null -> {
                closeGap()
                cells += LaneCell(span.event, span.endCol - col + 1)
                col = span.endCol + 1
            }
            timed != null -> {
                closeGap()
                cells += LaneCell(timed, 1)
                col += 1
            }
            else -> {
                gap += 1
                col += 1
            }
        }
    }
    closeGap()
    return cells
}

@Composable
private fun LaneRow(week: MonthWeek, lane: Int, dark: Boolean, soften: Boolean, colW: Dp) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        week.laneCells(lane).forEach { cell ->
            if (cell.event == null) {
                Spacer(GlanceModifier.width(colW * cell.cols).height(LANE_HEIGHT))
            } else {
                SpanBar(
                    event = cell.event,
                    dark = dark,
                    soften = soften,
                    width = colW * cell.cols,
                )
            }
        }
    }
}

/** A single connected, rounded event bar [width] wide with its clipped title. */
@Composable
private fun SpanBar(event: EventInstance, dark: Boolean, soften: Boolean, width: Dp) {
    val context = LocalContext.current
    val fill = eventFill(event.color, dark, soften)
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
                .background(fill),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = event.title.ifBlank { context.getString(R.string.event_untitled) },
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(eventInk(fill)),
                    fontSize = 9.sp,
                    textDecoration = glanceDeclinedDecoration(event.isDeclined),
                ),
                modifier = GlanceModifier.padding(horizontal = 3.dp),
            )
        }
    }
}

/** The "+N" row, drawn only for weeks that hide something, gaps merged as in a lane. */
@Composable
private fun OverflowRow(hidden: List<Int>, colW: Dp) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        var gap = 0
        hidden.forEach { count ->
            if (count == 0) {
                gap += 1
                return@forEach
            }
            if (gap > 0) {
                Spacer(GlanceModifier.width(colW * gap).height(LANE_HEIGHT))
                gap = 0
            }
            Text(
                text = "+$count",
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 9.sp),
                modifier = GlanceModifier.width(colW).padding(start = 3.dp),
            )
        }
        if (gap > 0) Spacer(GlanceModifier.width(colW * gap).height(LANE_HEIGHT))
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
