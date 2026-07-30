package de.jeanlucmakiola.calendula.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
import de.jeanlucmakiola.calendula.data.prefs.is24Hour
import de.jeanlucmakiola.calendula.data.prefs.resolveFirstDay
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.agenda.AgendaDay
import de.jeanlucmakiola.calendula.ui.agenda.AgendaRange
import de.jeanlucmakiola.calendula.ui.agenda.agendaRange
import de.jeanlucmakiola.calendula.ui.agenda.groupAgendaDays
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * How far either side of today the month widget pre-loads. The displayed month
 * is chosen reactively in the composition, so one wide read covers ~13 months of
 * prev/next navigation without re-querying on every arrow tap.
 */
private const val MONTH_WIDGET_RANGE_DAYS = 400

internal fun systemZone(): TimeZone = TimeZone.currentSystemDefault()

internal fun today(zone: TimeZone): LocalDate =
    Clock.System.now().toLocalDateTime(zone).date

internal fun Context.hasCalendarPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) ==
        PackageManager.PERMISSION_GRANTED

/** Snapshot rendered by the agenda widget. */
sealed interface AgendaWidgetData {
    /** Calendar permission not granted — the widget can't read events. */
    data object NeedsPermission : AgendaWidgetData
    data class Ready(
        val today: LocalDate,
        /**
         * Days with events across the **widest** selectable window. The displayed
         * range is sliced from this in the composition (read reactively from
         * Glance state), so a range change is pure recomposition rather than a
         * flaky widget session restart — the same approach the month widget uses.
         * Kept unfiltered: hiding/dimming finished events also happens reactively
         * in the composition, against [now], for the same reliable-update reason.
         */
        val days: List<AgendaDay>,
        /** Resolved clock convention for event time labels (the time-format pref). */
        val is24Hour: Boolean,
        /** Whether event colours are softened to pastels, or shown raw (#36). */
        val soften: Boolean,
        /** First day of the week, for the calendar-aligned "this week" range. */
        val weekStart: DayOfWeek,
        /** Saved range pref — the fallback when an instance has no Glance state yet. */
        val savedRange: AgendaRange,
        /** Saved past-event display pref — the fallback before Glance state is set. */
        val savedPastDisplay: PastEventDisplay,
        /**
         * Saved "always show today" pref (#35) — anchors today at the top with a
         * placeholder even when it has no events left. The fallback read reactively
         * from Glance state before an instance has its own state set.
         */
        val savedShowToday: Boolean,
        /** Saved widget size step (#103) — the fallback before Glance state is set. */
        val savedWidgetSize: WidgetSize,
        /** Snapshot instant the data was read at, for "has this event ended?" tests. */
        val now: Instant,
    ) : AgendaWidgetData
}

/**
 * Source data for the month widget: a wide window of instances plus the
 * week-start preference and today. The widget computes each displayed month's
 * grid from this in-memory list (via `layoutMonthWeeks`) as the user pages,
 * so month navigation is pure recomposition — no reload, no flaky widget
 * session restart.
 */
sealed interface MonthWidgetSource {
    data object NeedsPermission : MonthWidgetSource
    data class Ready(
        val today: LocalDate,
        val weekStart: DayOfWeek,
        val instances: List<EventInstance>,
    ) : MonthWidgetSource
}

/**
 * Process-lived cache of the wide month window. Month navigation re-runs
 * `provideGlance` (via `updateAll`), and re-querying ~13 months of instances on
 * every arrow tap is what made paging feel sluggish — so we load once and reuse
 * the same snapshot for every nearby month. Invalidated by
 * [invalidateMonthWidgetCache] when calendar data changes (the freshness
 * receiver), and automatically when the day rolls over (the `today` guard).
 */
internal object MonthWidgetCache {
    @Volatile
    var data: MonthWidgetSource.Ready? = null
}

internal fun invalidateMonthWidgetCache() {
    MonthWidgetCache.data = null
}

/**
 * One-shot read of the upcoming agenda for the widget. Reuses the app's
 * [agendaRange] window and [groupAgendaDays] grouping, and the repository's
 * [first]-emitted snapshot already has hidden calendars filtered out.
 */
internal suspend fun Context.loadAgendaWidgetData(): AgendaWidgetData {
    if (!hasCalendarPermission()) return AgendaWidgetData.NeedsPermission
    val zone = systemZone()
    val anchor = today(zone)
    val ep = widgetEntryPoint()
    val prefs = ep.settingsPrefs()
    val savedRange = prefs.agendaWidgetRange.first()
    val savedPastDisplay = prefs.pastEventDisplay.first()
    val showToday = prefs.agendaShowToday.first()
    val weekStart = prefs.weekStart.first().resolveFirstDay(Locale.getDefault())
    // Load the widest selectable window once; the displayed range is sliced in
    // the composition from Glance state, so changing the range is a plain
    // recomposition and never depends on the widget session restarting.
    val window = agendaRange(anchor, AgendaRange.MAX_CUSTOM_DAYS - 1, zone)
    val windowEnd = anchor.plus(AgendaRange.MAX_CUSTOM_DAYS - 1, DateTimeUnit.DAY)
    val instances = ep.calendarRepository().instances(window).first()
    val is24Hour = prefs.timeFormat.first()
        .is24Hour(android.text.format.DateFormat.is24HourFormat(this))
    val soften = prefs.softenCalendarColors.first()
    return AgendaWidgetData.Ready(
        today = anchor,
        days = groupAgendaDays(anchor, windowEnd, instances, zone),
        is24Hour = is24Hour,
        soften = soften,
        weekStart = weekStart,
        savedRange = savedRange,
        savedPastDisplay = savedPastDisplay,
        savedShowToday = showToday,
        savedWidgetSize = prefs.widgetSize.first(),
        now = Clock.System.now(),
    )
}

/** One-shot wide read backing the month widget's grid for any nearby month. */
internal suspend fun Context.loadMonthWidgetSource(): MonthWidgetSource {
    if (!hasCalendarPermission()) return MonthWidgetSource.NeedsPermission
    val zone = systemZone()
    val anchor = today(zone)
    // Reuse the cached window unless the day changed (then it's stale for "today").
    MonthWidgetCache.data?.let { if (it.today == anchor) return it }
    val ep = widgetEntryPoint()
    val weekStart = ep.settingsPrefs().weekStart.first().resolveFirstDay(Locale.getDefault())
    val from = anchor.minus(MONTH_WIDGET_RANGE_DAYS, DateTimeUnit.DAY).atStartOfDayIn(zone)
    val to = anchor.plus(MONTH_WIDGET_RANGE_DAYS, DateTimeUnit.DAY).atTime(23, 59, 59).toInstant(zone)
    val instances = ep.calendarRepository().instances(from..to).first()
    return MonthWidgetSource.Ready(today = anchor, weekStart = weekStart, instances = instances)
        .also { MonthWidgetCache.data = it }
}
