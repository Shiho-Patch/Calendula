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

/** How far from the requested week [WeekViewModel.week] keeps other weeks cached. */
private const val WEEK_CACHE_DAYS = 28

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeekViewModel @Inject constructor(
    private val repository: CalendarRepository,
    settingsPrefs: SettingsPrefs,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val zone = TimeZone.currentSystemDefault()
    private val locale: Locale = Locale.getDefault()

    /** Whether to fade events that have already finished (display concern only). */
    val dimCompletedEvents: StateFlow<Boolean> = settingsPrefs.dimCompletedEvents
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = false,
        )

    private val todayDate: LocalDate
        get() = Clock.System.now().toLocalDateTime(zone).date

    /** First day of the week, from the Settings preference (AUTO → locale). */
    val firstDayOfWeek: StateFlow<DayOfWeek> = settingsPrefs.weekStart
        .map { it.resolveFirstDay(locale) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = DayOfWeek.MONDAY,
        )

    // Anchor is a representative day inside the visible week; the actual week
    // start is derived against [firstDayOfWeek], so changing the first-day
    // preference re-frames the same week instead of jumping.
    private val _anchor = MutableStateFlow(todayDate)

    /** The pager page the anchor's week sits on. */
    val anchorPage: StateFlow<Int> =
        combine(_anchor, firstDayOfWeek) { anchor, ws -> weekPageFor(anchor, ws) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = weekPageFor(todayDate, DayOfWeek.MONDAY),
            )

    /**
     * The anchor week's state. Once loaded it never falls back to [WeekUiState.Loading]:
     * moving the anchor keeps the last result until the new week arrives, since
     * the pages draw their own weeks and this only gates the failure screen.
     */
    val state: StateFlow<WeekUiState> =
        combine(_anchor, firstDayOfWeek) { anchor, ws -> anchor.startOfWeek(ws) }
            .distinctUntilChanged()
            .flatMapLatest { start -> week(start).filterNot { it is WeekUiState.Loading } }
            .catch { emit(WeekUiState.Failure(FailureReason.ProviderUnavailable)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = WeekUiState.Loading,
            )

    private val weeks = PageStateCache<LocalDate, WeekUiState>(
        scope = viewModelScope,
        initial = WeekUiState.Loading,
        // Far enough to cover the pages either side and a swipe back, so paging
        // through a year doesn't keep a year of queries around.
        keep = { cached, requested -> abs(cached.daysUntil(requested)) <= WEEK_CACHE_DAYS },
    ) { start ->
        combine(
            repository.calendars(),
            repository.instances(weekRange(start, zone)),
        ) { calendars, instances ->
            buildState(start, calendars, instances)
        }
            .catch { emit(WeekUiState.Failure(FailureReason.ProviderUnavailable)) }
            .flowOn(io)
    }

    /** The week starting at [start], shared between its pager page and [state]. Main thread only. */
    fun week(start: LocalDate): StateFlow<WeekUiState> = weeks.get(start)

    /** The pager came to rest on [page]; follow it unless it is already the anchor's. */
    fun onPageSettled(page: Int) {
        val ws = firstDayOfWeek.value
        if (weekPageFor(_anchor.value, ws) != page) _anchor.value = weekStartForPage(page, ws)
    }

    fun goToToday() {
        _anchor.value = todayDate
    }

    private fun buildState(
        start: LocalDate,
        calendars: List<CalendarSource>,
        instances: List<EventInstance>,
    ): WeekUiState {
        calendarListFailure(calendars)?.let { return WeekUiState.Failure(it) }
        val days = (0 until 7).map { start.plus(it, DateTimeUnit.DAY) }
        val allDay = instances.filter { it.isAllDay }
        val timed = instances.filterNot { it.isAllDay }
        return WeekUiState.Success(
            weekStart = start,
            today = todayDate,
            days = days,
            allDaySpans = layoutAllDay(allDay, days, zone),
            timedByDay = days.associateWith { day -> layoutDay(timed, day, zone) },
        )
    }
}

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

/** Half-open instant range covering the seven days starting at [start]. */
internal fun weekRange(start: LocalDate, zone: TimeZone): ClosedRange<Instant> {
    val from = start.atStartOfDayIn(zone)
    val to = start.plus(6, DateTimeUnit.DAY).atTime(23, 59, 59).toInstant(zone)
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
