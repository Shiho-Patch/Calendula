package de.jeanlucmakiola.calendula.ui.month

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.data.prefs.firstDayOfWeek
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.FailureReason
import de.jeanlucmakiola.calendula.domain.calendarListFailure
import de.jeanlucmakiola.calendula.ui.common.PageStateCache
import de.jeanlucmakiola.calendula.domain.isDeclined
import de.jeanlucmakiola.calendula.ui.week.coversDay
import de.jeanlucmakiola.calendula.ui.week.layoutAllDay
import de.jeanlucmakiola.calendula.ui.week.spansMultipleDays
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import javax.inject.Inject

/** How many months either side of the requested one [MonthViewModel.month] keeps cached. */
private const val MONTH_CACHE_SPAN = 2

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MonthViewModel @Inject constructor(
    private val repository: CalendarRepository,
    settingsPrefs: SettingsPrefs,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val zone = TimeZone.currentSystemDefault()

    /** First day of the week, from the Settings preference (AUTO → locale). */
    val weekStart: StateFlow<DayOfWeek> = settingsPrefs.firstDayOfWeek(viewModelScope)

    /** Whether to fade events that have already finished (display concern only). */
    val dimCompletedEvents: StateFlow<Boolean> = settingsPrefs.dimCompletedEvents
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = false,
        )

    /** Whether to show the calendar-week number gutter (#25; display only). */
    val showWeekNumbers: StateFlow<Boolean> = settingsPrefs.showWeekNumbers
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = false,
        )

    /** How the grid is laid out and navigated (#38, #53). */
    val viewStyle: StateFlow<MonthViewStyle> = settingsPrefs.monthViewStyle
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = MonthViewStyle.Paged,
        )

    private val todayDate: LocalDate
        get() = Clock.System.now().toLocalDateTime(zone).date

    private val _month = MutableStateFlow(YearMonth(todayDate.year, todayDate.month))
    val month: StateFlow<YearMonth> = _month

    /** The pager page the anchor month sits on, for the paged and split styles. */
    val anchorPage: StateFlow<Int> = _month
        .map { monthIndexOf(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = monthIndexOf(_month.value),
        )

    /**
     * The anchor month's state. Once loaded it never falls back to
     * [MonthUiState.Loading]: moving the anchor keeps the last result until the
     * new month arrives, since the pages draw their own months and this feeds
     * the failure screen and the split style's day pane.
     */
    val state: StateFlow<MonthUiState> =
        combine(_month, weekStart, viewStyle) { ym, ws, style -> Triple(ym, ws, style) }
            .flatMapLatest { (ym, ws, style) ->
                // The mirror of the gate below: the paged grid is what Paged and
                // Split draw, so under a scrolling style this query is a month
                // of provider work for a view that isn't on screen.
                if (style.isScrolling) return@flatMapLatest flowOf(MonthUiState.Loading)
                month(ym, ws).filterNot { it is MonthUiState.Loading }
            }
            .catch { emit(MonthUiState.Failure(FailureReason.ProviderUnavailable)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = MonthUiState.Loading,
            )

    private val months = PageStateCache<Pair<YearMonth, DayOfWeek>, MonthUiState>(
        scope = viewModelScope,
        initial = MonthUiState.Loading,
        // Far enough to cover the pages either side and a swipe back, so paging
        // through a year doesn't keep a year of queries around. A week-start
        // change lays every month out afresh.
        keep = { (cached, cachedWs), (requested, ws) ->
            cachedWs == ws && abs(monthIndexOf(cached) - monthIndexOf(requested)) <= MONTH_CACHE_SPAN
        },
    ) { (ym, ws) ->
        combine(
            repository.calendars(),
            repository.instances(monthGridRange(ym, ws, zone)),
        ) { calendars, instances ->
            buildState(ym, ws, calendars, instances)
        }
            .catch { emit(MonthUiState.Failure(FailureReason.ProviderUnavailable)) }
            .flowOn(io)
    }

    /**
     * Month [ym] laid out with weeks starting on [ws], shared between its pager
     * page and [state]. Main thread only.
     */
    fun month(ym: YearMonth, ws: DayOfWeek): StateFlow<MonthUiState> = months.get(ym to ws)

    /** The pager came to rest on [page]; follow it unless it is already the anchor's. */
    fun onPageSettled(page: Int) {
        if (monthIndexOf(_month.value) == page) return
        _month.value = yearMonthForIndex(page)
        realignSelection()
    }

    // --- Continuous + Dense styles (#38) ----------------------------------
    //
    // These scroll through every month there is, so they can't load "a month" —
    // they load a sliding window of month indices around whatever is on screen.
    // The window only moves when the visible range comes within WINDOW_EDGE
    // months of a loaded edge, so a scroll re-queries occasionally rather than
    // on every frame.
    //
    // The window also *starts small and grows*. A provider Instances query
    // expands recurrences across its whole range, so opening straight onto a
    // year of months made the first frame wait for eleven months of expansion
    // when only one was about to be looked at. It now opens on INITIAL_PAD
    // months, and each completed load widens by GROWTH_STEP in both directions
    // until MAX_PAD — the months you can reach by scrolling arrive while you're
    // still looking at the first one.

    /** How far around the visible range we currently load; grows as loads land. */
    @Volatile
    private var loadPad = INITIAL_PAD

    /** Last reported visible range, so a widening step can re-centre on it. */
    @Volatile
    private var visibleMonths = monthIndexOf(YearMonth(todayDate.year, todayDate.month))
        .let { it..it }

    private val _loadedMonths = MutableStateFlow(
        monthIndexOf(YearMonth(todayDate.year, todayDate.month))
            .let { clampMonthWindow(it - INITIAL_PAD..it + INITIAL_PAD) },
    )

    val continuousState: StateFlow<ContinuousMonthUiState> =
        combine(_loadedMonths, weekStart, viewStyle) { window, ws, style ->
            Triple(window, ws, style)
        }
            .flatMapLatest { (window, ws, style) ->
                // Nothing to load for the paged and split styles — they have
                // their own single-month flow, and querying a year of months
                // behind them is pure waste.
                if (!style.isScrolling) return@flatMapLatest flowOf(ContinuousMonthUiState.Loading)
                // Widened to whole grid weeks at both ends: a month block still
                // has to know about an event that starts in the boundary week's
                // clipped-off days, or a bar running into the block would vanish.
                val first = firstOfMonth(yearMonthForIndex(window.first)).startOfGridWeek(ws)
                val last = firstOfMonth(yearMonthForIndex(window.last))
                    .plus(1, DateTimeUnit.MONTH)
                    .minus(1, DateTimeUnit.DAY)
                    .startOfGridWeek(ws)
                    .plus(6, DateTimeUnit.DAY)
                val range = first.atStartOfDayIn(zone)..last.atTime(23, 59, 59).toInstant(zone)
                combine(
                    repository.calendars(),
                    repository.instances(range),
                ) { calendars, instances ->
                    buildContinuousState(window, ws, style, calendars, instances)
                }
            }
            // A load landing is the cue to reach further out. Widening from here
            // rather than on a timer means each step waits for the previous one,
            // so the ladder can never outrun the provider.
            .onEach { if (it is ContinuousMonthUiState.Success) widenLoadedWindow() }
            .catch { emit(ContinuousMonthUiState.Failure(FailureReason.ProviderUnavailable)) }
            .flowOn(io)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = ContinuousMonthUiState.Loading,
            )

    /**
     * Report which absolute month indices are on screen. Cheap to call on every
     * scroll frame: it returns immediately unless the visible range has drifted
     * close enough to a loaded edge to warrant a wider query.
     */
    fun onVisibleMonthsChanged(firstIndex: Int, lastIndex: Int) {
        visibleMonths = firstIndex..lastIndex
        nextLoadWindow(_loadedMonths.value, firstIndex, lastIndex, loadPad)
            ?.let { _loadedMonths.value = clampMonthWindow(it) }
    }

    /**
     * One rung up the ladder: reach [GROWTH_STEP] further in each direction,
     * stopping at [MAX_PAD]. A no-op once there, so the provider notifications
     * that re-emit the same window don't restart it.
     */
    private fun widenLoadedWindow() {
        if (loadPad >= MAX_PAD) return
        loadPad = (loadPad + GROWTH_STEP).coerceAtMost(MAX_PAD)
        _loadedMonths.value = clampMonthWindow(
            visibleMonths.first - loadPad..visibleMonths.last + loadPad,
        )
    }

    private fun buildContinuousState(
        window: IntRange,
        weekStart: DayOfWeek,
        style: MonthViewStyle,
        calendars: List<CalendarSource>,
        instances: List<EventInstance>,
    ): ContinuousMonthUiState {
        calendarListFailure(calendars)?.let { return ContinuousMonthUiState.Failure(it) }
        // Bucketed once for the whole window: a row then takes the handful of
        // events on its seven days instead of re-scanning a year of them.
        val byDay = DayIndex(instances, zone)
        // Only the style on screen is laid out. Building both doubled the work
        // for a layout nothing was going to draw.
        val months = if (style == MonthViewStyle.Continuous) {
            window.associateWith { index ->
                val ym = yearMonthForIndex(index)
                layoutMonthWeeks(ym, weekStart, byDay, zone).map { clipWeekToMonth(it, ym) }
            }
        } else {
            emptyMap()
        }
        val weeks = if (style == MonthViewStyle.Dense) {
            weekWindowFor(window, weekStart).associateWith { index ->
                val days = (0 until 7).map {
                    weekStartForIndex(index, weekStart).plus(it, DateTimeUnit.DAY)
                }
                layoutCalendarWeek(days, byDay.eventsOn(days), zone)
            }
        } else {
            emptyMap()
        }
        return ContinuousMonthUiState.Success(
            today = todayDate,
            monthsByIndex = months,
            weeksByIndex = weeks,
            weekStart = weekStart,
            zone = zone,
        )
    }

    // --- Split style (#53) ------------------------------------------------
    //
    // Null means the full-month agenda; a date narrows the pane to that day.

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate

    /**
     * Select [date], following it to its month if it sits in the grid's leading
     * or trailing days — tapping a greyed-out day should show that day, not
     * silently list a date the grid isn't pointing at.
     */
    fun selectDate(date: LocalDate) {
        val target = YearMonth(date.year, date.month)
        _selectedDate.value = if (target == _month.value && _selectedDate.value == date) null else date
        if (target != _month.value) _month.value = target
    }

    fun clearSelection() { _selectedDate.value = null }

    /** A pane swipe walks days when filtered, months when showing the agenda. */
    fun stepSplitPane(days: Int) {
        val selected = _selectedDate.value
        if (selected == null) {
            _month.value = yearMonthForIndex(monthIndexOf(_month.value) + days)
        } else {
            val next = selected.plus(days, DateTimeUnit.DAY)
            _selectedDate.value = next
            _month.value = YearMonth(next.year, next.month)
        }
    }

    /** Moving the grid to another month restores its full agenda. */
    private fun realignSelection() {
        _selectedDate.value = null
    }

    fun goToToday() {
        _month.value = YearMonth(todayDate.year, todayDate.month)
        _selectedDate.value = null
    }

    /**
     * Track [_month] to the month a scrolling style is showing. Those styles
     * navigate by scroll position and never set [_month] themselves, so it would
     * otherwise sit wherever paged navigation last left it (today's month, on a
     * fresh open) — and a switch to a paged style, or a reseed of the other
     * scrolling style's list, would jump there instead of holding position. The
     * selection is cleared alongside it so Split opens the visible month's agenda.
     */
    fun syncScrollMonth(ym: YearMonth) {
        if (_month.value == ym) return
        _month.value = ym
        _selectedDate.value = null
    }

    /** Jump to the month containing [date] (drawer jump-to-date). */
    fun goToDate(date: LocalDate) {
        _month.value = YearMonth(date.year, date.month)
        _selectedDate.value = date
    }

    private fun buildState(
        ym: YearMonth,
        weekStart: DayOfWeek,
        calendars: List<CalendarSource>,
        instances: List<EventInstance>,
    ): MonthUiState {
        calendarListFailure(calendars)?.let { return MonthUiState.Failure(it) }
        val weeks = layoutMonthWeeks(ym, weekStart, instances, zone)
        return MonthUiState.Success(
            month = ym,
            today = todayDate,
            weeks = weeks,
            instancesByDay = instancesByDay(weeks.flatMap { it.days }, instances, zone),
            zone = zone,
        )
    }
}

/**
 * Split the month grid into week rows and resolve each row's events. An event is
 * a spanning bar when it's all-day or touches more than one of the row's days;
 * everything else is a single-day timed pill. Bars get lanes from the shared
 * [layoutAllDay] so a multi-day event stays on one row across the week.
 *
 * Shared by the Month screen and the month home-screen widget so both lay out
 * spans, lanes and per-day counts identically.
 */
internal fun layoutMonthWeeks(
    ym: YearMonth,
    weekStart: DayOfWeek,
    instances: List<EventInstance>,
    zone: TimeZone,
): List<MonthWeek> = layoutMonthWeeks(ym, weekStart, DayIndex(instances, zone), zone)

/**
 * As above, but reusing a [DayIndex] already built for a wider range — what the
 * scrolling styles need, since they lay out many months from one query and
 * rebuilding the index per month would put the cost straight back.
 */
internal fun layoutMonthWeeks(
    ym: YearMonth,
    weekStart: DayOfWeek,
    byDay: DayIndex,
    zone: TimeZone,
): List<MonthWeek> {
    val gridStart = firstOfMonth(ym).startOfGridWeek(weekStart)
    val weekCount = weekRowsInMonth(ym, weekStart)

    return (0 until weekCount).map { row ->
        val days = (0 until 7).map { gridStart.plus(row * 7 + it, DateTimeUnit.DAY) }
        layoutCalendarWeek(days, byDay.eventsOn(days), zone)
    }
}

/**
 * Events bucketed by the dates they cover, built once per load.
 *
 * [layoutCalendarWeek] opens by filtering the instances it was handed down to
 * the ones touching its seven days. That is fine for a single month's grid, but
 * the scrolling styles lay out a hundred-odd rows from one query, and scanning
 * *every* instance in an eleven-month window for each of them made the work grow
 * with the number of enabled calendars until opening the view stalled.
 *
 * Membership is decided by [coversDay] itself rather than by re-deriving the
 * rule from the timestamps: the all-day and timed cases have enough edge cases
 * between them (UTC anchoring, exclusive ends, zero-length events at midnight)
 * that a second implementation would drift. The walk only visits an event's own
 * candidate days, so the check runs a couple of times per event rather than once
 * per event per row.
 */
internal class DayIndex(instances: List<EventInstance>, private val zone: TimeZone) {

    private val byDay: Map<LocalDate, List<EventInstance>> = buildMap<LocalDate, MutableList<EventInstance>> {
        instances.forEach { event ->
            // All-day events are anchored to UTC midnights; timed ones to the
            // device zone. Either way the candidate span is the event's own
            // dates, which is one or two days for almost everything.
            val anchor = if (event.isAllDay) TimeZone.UTC else zone
            var day = event.start.toLocalDateTime(anchor).date
            val lastCandidate = event.end.toLocalDateTime(anchor).date
            while (day <= lastCandidate) {
                if (event.coversDay(day, zone)) getOrPut(day) { mutableListOf() }.add(event)
                day = day.plus(1, DateTimeUnit.DAY)
            }
        }
    }

    /** Every event touching any of [days], each listed once, in input order. */
    fun eventsOn(days: List<LocalDate>): List<EventInstance> {
        if (days.isEmpty()) return emptyList()
        val hits = days.flatMap { byDay[it].orEmpty() }
        // A multi-day event is bucketed on each date it covers, so a row that
        // holds several of its days would otherwise list it several times.
        return if (hits.size < 2) hits else hits.distinctBy { it.instanceId }
    }
}

/**
 * Resolve one week row's events for rendering. Split out of [layoutMonthWeeks] so
 * the continuous style — which streams weeks rather than months and so has no
 * enclosing [YearMonth] to slice by — lays each row out identically.
 *
 * [days] must be the row's seven consecutive dates, in display order.
 */
internal fun layoutCalendarWeek(
    days: List<LocalDate>,
    instances: List<EventInstance>,
    zone: TimeZone,
): MonthWeek {
    val weekEvents = instances.filter { ev -> days.any { ev.coversDay(it, zone) } }
    // A bar is decided by the *event*, not by how much of it this row holds: one
    // crossing midnight into the next week row covers a single day in each, and
    // counting per row drew it as two unrelated pills (#253).
    val (bars, singles) = weekEvents.partition { ev ->
        ev.isAllDay || ev.spansMultipleDays(zone)
    }
    val spans = layoutAllDay(bars, days, zone).map { s ->
        MonthSpan(
            event = s.event,
            startCol = s.startCol,
            endCol = s.endCol,
            lane = s.lane,
            continuesLeft = s.event.coversDay(days.first().minus(1, DateTimeUnit.DAY), zone),
            continuesRight = s.event.coversDay(days.last().plus(1, DateTimeUnit.DAY), zone),
        )
    }
    return MonthWeek(
        days = days,
        spans = spans,
        timedByDay = days.associateWith { d ->
            // Declined last, so a day that overflows drops the events you said
            // no to before the ones you are actually going to (#230).
            singles.filter { it.coversDay(d, zone) }
                .sortedWith(compareBy<EventInstance> { it.isDeclined }.thenBy { it.start })
        },
        countByDay = days.associateWith { d -> weekEvents.count { it.coversDay(d, zone) } },
    )
}

/**
 * Every event touching each of [days], declined ones last (#230), then all-day
 * first and by start time within each group. Unlike
 * [MonthWeek.timedByDay] this keeps multi-day and all-day events on every date
 * they cover and applies no display cap, so the split style's day pane can list a
 * date in full without querying the provider again.
 */
internal fun instancesByDay(
    days: List<LocalDate>,
    instances: List<EventInstance>,
    zone: TimeZone,
): Map<LocalDate, List<EventInstance>> =
    days.associateWith { day ->
        instances
            .filter { it.coversDay(day, zone) }
            .sortedWith(
                compareBy<EventInstance> { it.isDeclined }
                    .thenByDescending { it.isAllDay }
                    .thenBy { it.start },
            )
    }

/**
 * The on-screen grid spans 6 weeks anchored on [weekStart]. Includes the
 * trailing days of the previous month and the leading days of the next month.
 */
internal fun monthGridRange(
    ym: YearMonth,
    weekStart: DayOfWeek,
    zone: TimeZone,
): ClosedRange<Instant> {
    val firstOfMonth = LocalDate(ym.year, ym.month, 1)
    val gridStart = firstOfMonth.startOfGridWeek(weekStart)
    val gridEnd = gridStart.plus(41, DateTimeUnit.DAY)
    val start = gridStart.atStartOfDayIn(zone)
    val end = gridEnd.atTime(23, 59, 59).toInstant(zone)
    return start..end
}

/**
 * The sliding window's shape.
 *
 * [INITIAL_PAD] is what the first frame waits for — one month either side of the
 * visible one, so opening the view costs about what the paged style costs. Each
 * completed load then reaches [GROWTH_STEP] further out until [MAX_PAD], filling
 * in the months a scroll could reach while the first ones are already on screen.
 *
 * [WINDOW_EDGE] is how close the visible range may drift to a loaded edge before
 * it reloads. It is always kept below the current pad — a trigger at or beyond
 * the pad would re-fire the moment its own reload landed.
 */
private const val INITIAL_PAD = 1
private const val GROWTH_STEP = 2
private const val MAX_PAD = 5
private const val WINDOW_EDGE = 2

/** The reload trigger for a given pad, held strictly inside it. */
internal fun edgeForPad(pad: Int): Int = minOf(WINDOW_EDGE, pad - 1).coerceAtLeast(0)

/**
 * First day shown in the split agenda: today in the current month, otherwise
 * the first of the visible month.
 */
internal fun agendaStartForMonth(month: YearMonth, today: LocalDate): LocalDate =
    if (YearMonth(today.year, today.month) == month) {
        today
    } else {
        LocalDate(month.year, month.month, 1)
    }

/**
 * The window to load for a visible range, or null to keep the current one.
 *
 * Kept pure and separate from the view model so the hysteresis — the reason a
 * scroll doesn't re-query the provider on every frame — is testable on its own.
 */
internal fun nextLoadWindow(
    loaded: IntRange,
    firstVisible: Int,
    lastVisible: Int,
    pad: Int = MAX_PAD,
): IntRange? {
    val edge = edgeForPad(pad)
    val comfortablyInside =
        firstVisible - edge >= loaded.first && lastVisible + edge <= loaded.last
    if (comfortablyInside) return null
    return (firstVisible - pad)..(lastVisible + pad)
}

/**
 * The continuous grid addresses months by an absolute index, so the list has one
 * stable, gap-free coordinate space to scroll through and key its items by.
 * Index 0 is January 1900; the list runs to [continuousMonthCount].
 *
 * Unlike the week indexing this replaced, the coordinate space doesn't depend on
 * the week-start preference — changing it reflows the rows *inside* a month
 * block but never moves the block, so a scroll position stays put.
 */
private const val MONTH_INDEX_EPOCH_YEAR = 1900
private const val MONTH_INDEX_END_YEAR = 2100

internal fun monthIndexOf(ym: YearMonth): Int =
    (ym.year - MONTH_INDEX_EPOCH_YEAR) * 12 + ym.month.ordinal

internal fun yearMonthForIndex(index: Int): YearMonth = YearMonth(
    MONTH_INDEX_EPOCH_YEAR + index / 12,
    Month.entries[index % 12],
)

/** Total months the continuous grid scrolls through (1900 → 2100). */
internal fun continuousMonthCount(): Int =
    (MONTH_INDEX_END_YEAR - MONTH_INDEX_EPOCH_YEAR + 1) * 12

/**
 * Each month occupies two LazyColumn items — its sticky header and the block of
 * week rows under it — so the two coordinate spaces differ by a factor of two.
 * Scrolling to a month means scrolling to its header.
 */
internal fun itemIndexForMonth(monthIndex: Int): Int = monthIndex * 2

internal fun monthIndexForItem(itemIndex: Int): Int = itemIndex / 2

/**
 * Hold a load window inside the months that actually exist. The pad
 * [nextLoadWindow] adds can otherwise push an index past either end of the
 * 1900–2100 span, and [yearMonthForIndex] has no month to hand back for one.
 */
internal fun clampMonthWindow(window: IntRange): IntRange {
    val last = continuousMonthCount() - 1
    return window.first.coerceIn(0, last)..window.last.coerceIn(0, last)
}

internal fun firstOfMonth(ym: YearMonth): LocalDate = LocalDate(ym.year, ym.month, 1)

/**
 * The Dense style addresses *weeks* by absolute index instead: it has no month
 * blocks to key by, and a row's identity has to survive the months around it
 * scrolling past. Index 0 is the first week of 1900 under the active week-start,
 * so — like the month space — every index is non-negative and the LazyColumn's
 * item indices and week indices are the same number.
 *
 * Unlike month indices these *do* shift with the week-start preference, which is
 * why the list state is keyed on it.
 */
private val WEEK_INDEX_EPOCH = LocalDate(1900, 1, 1)
private val WEEK_INDEX_END = LocalDate(2100, 12, 31)

internal fun weekIndexOf(date: LocalDate, weekStart: DayOfWeek): Int {
    val base = WEEK_INDEX_EPOCH.startOfGridWeek(weekStart)
    return base.daysUntil(date.startOfGridWeek(weekStart)) / 7
}

internal fun weekStartForIndex(index: Int, weekStart: DayOfWeek): LocalDate =
    WEEK_INDEX_EPOCH.startOfGridWeek(weekStart).plus(index * 7, DateTimeUnit.DAY)

/** Total weeks the Dense grid scrolls through (1900 → 2100). */
internal fun continuousWeekCount(weekStart: DayOfWeek): Int =
    weekIndexOf(WEEK_INDEX_END, weekStart) + 1

/** Which month a Dense row belongs to, for reporting the visible range. */
internal fun monthIndexForWeek(weekIndex: Int, weekStart: DayOfWeek): Int {
    // The row's midweek day: a boundary week is reported as whichever month owns
    // most of it, the same rule the title uses.
    val midweek = weekStartForIndex(weekIndex, weekStart).plus(3, DateTimeUnit.DAY)
    return monthIndexOf(YearMonth(midweek.year, midweek.month))
}

/**
 * Every week index the months in [window] touch — the Dense rows one month-window
 * load covers. Widened by a week at each end so the boundary rows either side are
 * laid out too, rather than flickering as placeholders.
 */
internal fun weekWindowFor(window: IntRange, weekStart: DayOfWeek): IntRange {
    val first = weekIndexOf(firstOfMonth(yearMonthForIndex(window.first)), weekStart) - 1
    val lastMonthEnd = firstOfMonth(yearMonthForIndex(window.last))
        .plus(1, DateTimeUnit.MONTH)
        .minus(1, DateTimeUnit.DAY)
    return first..(weekIndexOf(lastMonthEnd, weekStart) + 1)
}

/** How many week rows [ym] needs under [weekStart] — 4 (a non-leap February), 5 or 6. */
internal fun weekRowsInMonth(ym: YearMonth, weekStart: DayOfWeek): Int {
    val first = firstOfMonth(ym)
    val leadOffset = ((first.dayOfWeek.ordinal - weekStart.ordinal) + 7) % 7
    val daysInMonth = java.time.YearMonth.of(ym.year, ym.month.ordinal + 1).lengthOfMonth()
    return (leadOffset + daysInMonth + 6) / 7
}

/**
 * Strip everything outside [month] from a boundary week row, for the continuous
 * style's self-contained month blocks.
 *
 * The seven [MonthWeek.days] stay put — the row keeps its column geometry, and
 * the grid decides which cells to draw blank — but the neighbour month's events
 * are removed: its pills and counts go, and a bar reaching in from (or out into)
 * it is cut back to the month's own columns. A bar cut this way keeps a flat
 * cap, which is what says "this continues past the block" rather than "it ends
 * here".
 */
internal fun clipWeekToMonth(week: MonthWeek, month: YearMonth): MonthWeek {
    val inMonth = week.days.map { it.year == month.year && it.month == month.month }
    val firstCol = inMonth.indexOfFirst { it }
    val lastCol = inMonth.indexOfLast { it }
    // Can't happen for a row of the month's own grid, but a caller-proof no-op
    // beats an out-of-range crash.
    if (firstCol == -1) {
        return week.copy(spans = emptyList(), timedByDay = emptyMap(), countByDay = emptyMap())
    }
    val spans = week.spans.mapNotNull { span ->
        val start = maxOf(span.startCol, firstCol)
        val end = minOf(span.endCol, lastCol)
        if (start > end) {
            null
        } else {
            span.copy(
                startCol = start,
                endCol = end,
                continuesLeft = span.continuesLeft || start > span.startCol,
                continuesRight = span.continuesRight || end < span.endCol,
            )
        }
    }
    val own = week.days.filterIndexed { col, _ -> inMonth[col] }
    return week.copy(
        spans = spans,
        timedByDay = week.timedByDay.filterKeys { it in own },
        countByDay = week.countByDay.filterKeys { it in own },
    )
}

internal fun LocalDate.startOfGridWeek(weekStart: DayOfWeek): LocalDate {
    // DayOfWeek.ordinal: MONDAY=0..SUNDAY=6 → identical to ISO ordering.
    val offset = ((dayOfWeek.ordinal - weekStart.ordinal) + 7) % 7
    return minus(offset, DateTimeUnit.DAY)
}
