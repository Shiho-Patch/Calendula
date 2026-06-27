package de.jeanlucmakiola.calendula.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import de.jeanlucmakiola.calendula.data.prefs.resolveFirstDay
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.agenda.AgendaDay
import de.jeanlucmakiola.calendula.ui.agenda.agendaRange
import de.jeanlucmakiola.calendula.ui.agenda.dayCount
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
    data class Ready(val today: LocalDate, val days: List<AgendaDay>) : AgendaWidgetData
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
    val range = ep.settingsPrefs().agendaWidgetRange.first()
    val window = agendaRange(anchor, range.dayCount() - 1, zone)
    val instances = ep.calendarRepository().instances(window).first()
    return AgendaWidgetData.Ready(today = anchor, days = groupAgendaDays(anchor, instances, zone))
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
