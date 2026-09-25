package de.jeanlucmakiola.calendula.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.data.prefs.resolveFirstDay
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.FailureReason
import de.jeanlucmakiola.calendula.domain.calendarListFailure
import de.jeanlucmakiola.calendula.domain.isDeclined
import de.jeanlucmakiola.calendula.ui.common.PageStateCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant
import javax.inject.Inject
import kotlin.math.abs

const val MINUTES_PER_DAY: Int = 24 * 60

/** How far from the requested page [PagedTimelineViewModel.span] keeps other pages cached. */
private const val SPAN_CACHE_DAYS = 28

/** A run of [count] days from [start]: what one timeline page shows. */
data class DaySpan(val start: LocalDate, val count: Int)

/** How the pager cuts the calendar, and the page the anchor sits on. */
data class PagerPosition(val grid: PageGrid, val page: Int)

/**
 * A timeline paged in runs of days: the week view, and the multi-day view (#193).
 * The pager leads; the anchor follows it once it settles, and the grid it is
 * cut by is derived from the anchor through [gridRule], so a subclass decides
 * whether a jump re-frames the pages or only moves between them.
 *
 * @param gridRule the grid to use for a given anchor, as the settings behind it change.
 * @param initialRule the rule used until [gridRule] first emits.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class PagedTimelineViewModel(
    private val repository: CalendarRepository,
    settingsPrefs: SettingsPrefs,
    private val io: CoroutineDispatcher,
    gridRule: Flow<(LocalDate) -> PageGrid>,
    initialRule: (LocalDate) -> PageGrid,
) : ViewModel() {

    private val zone = TimeZone.currentSystemDefault()

    /** Whether to fade events that have already finished (display concern only). */
    val dimCompletedEvents: StateFlow<Boolean> = settingsPrefs.dimCompletedEvents
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = false,
        )

    private val todayDate: LocalDate
        get() = Clock.System.now().toLocalDateTime(zone).date

    // A day on the visible page; the page itself comes from the grid, so a
    // settings change re-frames the same days instead of jumping.
    private val _anchor = MutableStateFlow(todayDate)

    val position: StateFlow<PagerPosition> =
        combine(_anchor, gridRule) { anchor, rule -> rule(anchor).let { PagerPosition(it, it.pageFor(anchor)) } }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = initialRule(todayDate).let { PagerPosition(it, it.pageFor(todayDate)) },
            )

    /**
     * The anchor page's state. Once loaded it never falls back to [WeekUiState.Loading]:
     * moving the anchor keeps the last result until the new page arrives, since
     * the pages draw their own days and this only gates the failure screen.
     */
    val state: StateFlow<WeekUiState> =
        position.map { DaySpan(it.grid.startForPage(it.page), it.grid.dayCount) }
            .distinctUntilChanged()
            .flatMapLatest { span -> span(span).filterNot { it is WeekUiState.Loading } }
            .catch { emit(WeekUiState.Failure(FailureReason.ProviderUnavailable)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = WeekUiState.Loading,
            )

    private val spans = PageStateCache<DaySpan, WeekUiState>(
        scope = viewModelScope,
        initial = WeekUiState.Loading,
        // Far enough to cover the pages either side and a swipe back, so paging
        // through a year doesn't keep a year of queries around.
        keep = { cached, requested ->
            cached.count == requested.count &&
                abs(cached.start.daysUntil(requested.start)) <= SPAN_CACHE_DAYS
        },
    ) { span ->
        combine(
            repository.calendars(),
            repository.instances(spanRange(span.start, span.count, zone)),
        ) { calendars, instances ->
            buildState(span, calendars, instances)
        }
            .catch { emit(WeekUiState.Failure(FailureReason.ProviderUnavailable)) }
            .flowOn(io)
    }

    /** The days of one page, shared between that page and [state]. Main thread only. */
    fun span(span: DaySpan): StateFlow<WeekUiState> = spans.get(span)

    /** The pager came to rest on [page]; follow it unless it is already the anchor's. */
    fun onPageSettled(page: Int) {
        val grid = position.value.grid
        if (grid.pageFor(_anchor.value) != page) _anchor.value = grid.startForPage(page)
    }

    /** Move the anchor to [date], re-framing the pages if the grid depends on it. */
    fun jumpTo(date: LocalDate) {
        _anchor.value = date
    }

    fun goToToday() = jumpTo(todayDate)

    private fun buildState(
        span: DaySpan,
        calendars: List<CalendarSource>,
        instances: List<EventInstance>,
    ): WeekUiState {
        calendarListFailure(calendars)?.let { return WeekUiState.Failure(it) }
        val days = (0 until span.count).map { span.start.plus(it, DateTimeUnit.DAY) }
        val allDay = instances.filter { it.isAllDay }
        val timed = instances.filterNot { it.isAllDay }
        return WeekUiState.Success(
            weekStart = span.start,
            today = todayDate,
            days = days,
            allDaySpans = layoutAllDay(allDay, days, zone),
            timedByDay = days.associateWith { day -> layoutDay(timed, day, zone) },
        )
    }
}

/** Whole weeks, starting on the first day from Settings (AUTO → locale). */
@HiltViewModel
class WeekViewModel @Inject constructor(
    repository: CalendarRepository,
    settingsPrefs: SettingsPrefs,
    @IoDispatcher io: CoroutineDispatcher,
) : PagedTimelineViewModel(
    repository = repository,
    settingsPrefs = settingsPrefs,
    io = io,
    gridRule = settingsPrefs.weekStart.map { pref ->
        val grid = WeekGrid(pref.resolveFirstDay(Locale.getDefault()))
        val rule: (LocalDate) -> PageGrid = { grid }
        rule
    },
    initialRule = { WeekGrid(DayOfWeek.MONDAY) },
)

/**
 * The multi-day view (#193): runs of the day count from Settings, with the
 * anchor always a page's first day, so today — or a date jumped to — opens in
 * the first column.
 */
@HiltViewModel
class MultiDayViewModel @Inject constructor(
    repository: CalendarRepository,
    settingsPrefs: SettingsPrefs,
    @IoDispatcher io: CoroutineDispatcher,
) : PagedTimelineViewModel(
    repository = repository,
    settingsPrefs = settingsPrefs,
    io = io,
    gridRule = settingsPrefs.multiDayCount.map { count ->
        { anchor: LocalDate -> DaysGrid.startingAt(anchor, count) }
    },
    initialRule = { DaysGrid.startingAt(it, SettingsPrefs.DEFAULT_MULTI_DAY_COUNT) },
)

/**
 * Lay out all-day events as connected horizontal spans across the visible week.
 * Each event becomes one [AllDaySpan] from its first to its last covered column;
 * overlapping spans are stacked on separate lanes (greedy first-fit).
 */
internal fun layoutAllDay(
    events: List<EventInstance>,
    days: List<LocalDate>,
    zone: TimeZone,
): List<AllDaySpan> {
    data class Raw(val event: EventInstance, val startCol: Int, val endCol: Int)

    val raw = events
        .mapNotNull { ev ->
            val covered = days.indices.filter { ev.coversDay(days[it], zone) }
            if (covered.isEmpty()) null else Raw(ev, covered.first(), covered.last())
        }
        // Declined bars are packed after every other one, so on any day they
        // cover they land in a lane below it (#230).
        .sortedWith(compareBy({ it.event.isDeclined }, { it.startCol }, { it.endCol }))

    // What each lane already holds, rather than just how far right it reaches.
    // A single "last occupied column" only answers correctly while spans arrive
    // in non-decreasing start order, which the declined-last rule above breaks:
    // a Monday bar seated after a Wednesday one would be refused a lane it is
    // nowhere near, and each wasted lane costs the all-day strip a whole row and
    // pushes a bar closer to the month grid's lane cap. Seven columns
    // and a handful of bars, so the scan is cheaper than the sort above it.
    val laneCols = ArrayList<MutableList<IntRange>>()
    return raw.map { r ->
        val cols = r.startCol..r.endCol
        var lane = laneCols.indexOfFirst { seated -> seated.none { it overlaps cols } }
        if (lane == -1) {
            laneCols.add(mutableListOf(cols))
            lane = laneCols.size - 1
        } else {
            laneCols[lane].add(cols)
        }
        AllDaySpan(r.event, r.startCol, r.endCol, lane)
    }
}

/** Whether two column ranges share a column, so they cannot share a lane. */
private infix fun IntRange.overlaps(other: IntRange): Boolean =
    first <= other.last && other.first <= last

/** Beginning of the week (at [weekStart]) that contains this date. */
internal fun LocalDate.startOfWeek(weekStart: DayOfWeek): LocalDate {
    // DayOfWeek.ordinal: MONDAY=0..SUNDAY=6 → identical to ISO ordering.
    val offset = ((dayOfWeek.ordinal - weekStart.ordinal) + 7) % 7
    return minus(offset, DateTimeUnit.DAY)
}

/** Instant range covering the [count] days starting at [start]. */
internal fun spanRange(start: LocalDate, count: Int, zone: TimeZone): ClosedRange<Instant> {
    val from = start.atStartOfDayIn(zone)
    val to = start.plus(count - 1, DateTimeUnit.DAY).atTime(23, 59, 59).toInstant(zone)
    return from..to
}

/** True if this event overlaps the calendar [day] in [zone] (any portion). */
internal fun EventInstance.coversDay(day: LocalDate, zone: TimeZone): Boolean {
    if (isAllDay) {
        // All-day events live at UTC midnights with an exclusive end. Compare
        // calendar dates in UTC and step the exclusive end back to the last
        // covered day (mirroring the detail/edit views), so a one-day event
        // covers exactly its single date. Slicing the day in the device zone
        // would push the exclusive end a few hours into the next local day
        // east of UTC, making the event leak onto day + 1.
        val startDate = start.toLocalDateTime(TimeZone.UTC).date
        val endExclusive = end.toLocalDateTime(TimeZone.UTC).date
        val lastDay = maxOf(startDate, endExclusive.minus(1, DateTimeUnit.DAY))
        return day in startDate..lastDay
    }
    val dayStart = day.atStartOfDayIn(zone)
    val dayEnd = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)
    return start < dayEnd && end > dayStart
}

/**
 * True if this event touches more than one calendar day in [zone] — an all-day
 * event covering a range, or a timed one crossing midnight. Occurrences are
 * contiguous, so covering the day after the first is the whole question.
 */
internal fun EventInstance.spansMultipleDays(zone: TimeZone): Boolean {
    val anchor = if (isAllDay) TimeZone.UTC else zone
    val secondDay = start.toLocalDateTime(anchor).date.plus(1, DateTimeUnit.DAY)
    return coversDay(secondDay, zone)
}

/**
 * Clip [events] to a single [day] and assign lanes so overlapping events render
 * side-by-side. Lane count is computed per overlap-cluster (a maximal run of
 * chained-overlapping events), matching the common phone week-view behaviour.
 *
 * All-day events are ignored here — they live in the all-day strip.
 */
internal fun layoutDay(
    events: List<EventInstance>,
    day: LocalDate,
    zone: TimeZone,
): List<TimedBlock> {
    val dayStart = day.atStartOfDayIn(zone)
    val dayEnd = day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)

    data class Raw(val event: EventInstance, val startMin: Int, val endMin: Int)

    val raw = events.asSequence()
        .filterNot { it.isAllDay }
        .mapNotNull { ev ->
            if (ev.start == ev.end) {
                // Instant event: keep only if the point falls inside this day.
                if (ev.start < dayStart || ev.start >= dayEnd) return@mapNotNull null
                val m = (ev.start - dayStart).inWholeMinutes.toInt().coerceIn(0, MINUTES_PER_DAY)
                Raw(ev, m, m)
            } else {
                val s = maxOf(ev.start, dayStart)
                val e = minOf(ev.end, dayEnd)
                if (e <= s) return@mapNotNull null
                val startMin = (s - dayStart).inWholeMinutes.toInt().coerceIn(0, MINUTES_PER_DAY)
                val endMin = (e - dayStart).inWholeMinutes.toInt().coerceIn(startMin, MINUTES_PER_DAY)
                Raw(ev, startMin, endMin)
            }
        }
        .sortedWith(compareBy({ it.startMin }, { it.endMin }))
        .toList()

    val result = ArrayList<TimedBlock>(raw.size)
    var i = 0
    while (i < raw.size) {
        // Grow a cluster of chained-overlapping events.
        var clusterEnd = raw[i].endMin
        var j = i + 1
        while (j < raw.size && raw[j].startMin < clusterEnd) {
            clusterEnd = maxOf(clusterEnd, raw[j].endMin)
            j++
        }
        val cluster = raw.subList(i, j)
        // Greedy first-fit column assignment (= max overlap depth in the cluster).
        val laneEnd = ArrayList<Int>()
        val lanes = IntArray(cluster.size)
        cluster.forEachIndexed { k, r ->
            var placed = laneEnd.indexOfFirst { it <= r.startMin }
            if (placed == -1) {
                laneEnd.add(r.endMin)
                placed = laneEnd.size - 1
            } else {
                laneEnd[placed] = r.endMin
            }
            lanes[k] = placed
        }
        val laneCount = laneEnd.size
        cluster.forEachIndexed { k, r ->
            result.add(TimedBlock(r.event, r.startMin, r.endMin, lanes[k], laneCount))
        }
        i = j
    }
    return result
}
