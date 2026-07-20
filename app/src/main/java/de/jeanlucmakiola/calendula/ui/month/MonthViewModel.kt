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
import de.jeanlucmakiola.calendula.ui.week.coversDay
import de.jeanlucmakiola.calendula.ui.week.layoutAllDay
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
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

    val state: StateFlow<MonthUiState> =
        combine(_month, weekStart) { ym, ws -> ym to ws }
            .flatMapLatest { (ym, ws) ->
                val range = monthGridRange(ym, ws, zone)
                combine(
                    repository.calendars(),
                    repository.instances(range),
                ) { calendars, instances ->
                    buildState(ym, ws, calendars, instances)
                }
            }
            .catch { emit(MonthUiState.Failure(FailureReason.ProviderUnavailable)) }
            .flowOn(io)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = MonthUiState.Loading,
            )

    // --- Continuous style (#38) -------------------------------------------
    //
    // The continuous grid is one endless stream of weeks, so it can't load "a
    // month" — it loads a sliding window of week indices around whatever is on
    // screen. The window only moves when the visible range comes within
    // WINDOW_EDGE weeks of a loaded edge, so a scroll re-queries occasionally
    // rather than on every frame.

    // Seeded around today so the first frame has data. The week-start preference
    // hasn't arrived yet, but anchoring on a different day shifts an index by at
    // most one week — well inside the pad, and the first scroll report corrects it.
    private val _loadedWeeks = MutableStateFlow(
        weekIndexOf(todayDate, DayOfWeek.MONDAY).let { it - WINDOW_PAD..it + WINDOW_PAD },
    )

    val continuousState: StateFlow<ContinuousMonthUiState> =
        combine(_loadedWeeks, weekStart) { window, ws -> window to ws }
            .flatMapLatest { (window, ws) ->
                val first = weekStartForIndex(window.first, ws)
                val last = weekStartForIndex(window.last, ws).plus(6, DateTimeUnit.DAY)
                val range = first.atStartOfDayIn(zone)..last.atTime(23, 59, 59).toInstant(zone)
                combine(
                    repository.calendars(),
                    repository.instances(range),
                ) { calendars, instances ->
                    buildContinuousState(window, ws, calendars, instances)
                }
            }
            .catch { emit(ContinuousMonthUiState.Failure(FailureReason.ProviderUnavailable)) }
            .flowOn(io)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = ContinuousMonthUiState.Loading,
            )

    /**
     * Report which absolute week indices are on screen. Cheap to call on every
     * scroll frame: it returns immediately unless the visible range has drifted
     * close enough to a loaded edge to warrant a wider query.
     */
    fun onVisibleWeeksChanged(firstIndex: Int, lastIndex: Int) {
        nextLoadWindow(_loadedWeeks.value, firstIndex, lastIndex)?.let { _loadedWeeks.value = it }
    }

    private fun buildContinuousState(
        window: IntRange,
        weekStart: DayOfWeek,
        calendars: List<CalendarSource>,
        instances: List<EventInstance>,
    ): ContinuousMonthUiState {
        if (calendars.isEmpty()) {
            return ContinuousMonthUiState.Failure(FailureReason.NoCalendarsConfigured)
        }
        val weeks = window.associateWith { index ->
            val days = (0 until 7).map {
                weekStartForIndex(index, weekStart).plus(it, DateTimeUnit.DAY)
            }
            layoutCalendarWeek(days, instances, zone)
        }
        return ContinuousMonthUiState.Success(
            today = todayDate,
            weeksByIndex = weeks,
            weekStart = weekStart,
        )
    }

    fun goToPrev() {
        _month.value = _month.value.minus(1, DateTimeUnit.MONTH)
    }

    fun goToNext() {
        _month.value = _month.value.plus(1, DateTimeUnit.MONTH)
    }

    fun goToToday() {
        _month.value = YearMonth(todayDate.year, todayDate.month)
    }

    /** Jump to the month containing [date] (drawer jump-to-date). */
    fun goToDate(date: LocalDate) {
        _month.value = YearMonth(date.year, date.month)
    }

    private fun buildState(
        ym: YearMonth,
        weekStart: DayOfWeek,
        calendars: List<CalendarSource>,
        instances: List<EventInstance>,
    ): MonthUiState {
        if (calendars.isEmpty()) {
            return MonthUiState.Failure(FailureReason.NoCalendarsConfigured)
        }
        val weeks = layoutMonthWeeks(ym, weekStart, instances, zone)
        return MonthUiState.Success(
            month = ym,
            today = todayDate,
            weeks = weeks,
            instancesByDay = instancesByDay(weeks.flatMap { it.days }, instances, zone),
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
): List<MonthWeek> {
    val firstOfMonth = LocalDate(ym.year, ym.month, 1)
    val gridStart = firstOfMonth.startOfGridWeek(weekStart)
    val leadOffset = ((firstOfMonth.dayOfWeek.ordinal - weekStart.ordinal) + 7) % 7
    val daysInMonth =
        java.time.YearMonth.of(ym.year, ym.month.ordinal + 1).lengthOfMonth()
    val weekCount = (leadOffset + daysInMonth + 6) / 7

    return (0 until weekCount).map { row ->
        val days = (0 until 7).map { gridStart.plus(row * 7 + it, DateTimeUnit.DAY) }
        layoutCalendarWeek(days, instances, zone)
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
    val (bars, singles) = weekEvents.partition { ev ->
        ev.isAllDay || days.count { ev.coversDay(it, zone) } > 1
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
            singles.filter { it.coversDay(d, zone) }.sortedBy { it.start }
        },
        countByDay = days.associateWith { d -> weekEvents.count { it.coversDay(d, zone) } },
    )
}

/**
 * Every event touching each of [days], all-day first then by start time. Unlike
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
            .sortedWith(compareByDescending<EventInstance> { it.isAllDay }.thenBy { it.start })
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
 * How many weeks the continuous window loads beyond the visible range, and how
 * close the visible range may drift to a loaded edge before it reloads. The pad
 * is generous relative to the trigger so a steady scroll crosses the trigger
 * well before it would run out of laid-out weeks.
 */
private const val WINDOW_PAD = 12
private const val WINDOW_EDGE = 4

/**
 * The window to load for a visible range, or null to keep the current one.
 *
 * Kept pure and separate from the view model so the hysteresis — the reason a
 * scroll doesn't re-query the provider on every frame — is testable on its own.
 */
internal fun nextLoadWindow(loaded: IntRange, firstVisible: Int, lastVisible: Int): IntRange? {
    val comfortablyInside =
        firstVisible - WINDOW_EDGE >= loaded.first && lastVisible + WINDOW_EDGE <= loaded.last
    if (comfortablyInside) return null
    return (firstVisible - WINDOW_PAD)..(lastVisible + WINDOW_PAD)
}

/**
 * The continuous grid addresses weeks by an absolute index rather than a
 * (month, row) pair, so the list has one stable, gap-free coordinate space to
 * scroll through and key its items by. Index 0 is the first week of 1900 under
 * the active week-start; the list runs to [CONTINUOUS_WEEK_COUNT].
 *
 * The epoch is deliberately far in the past so every index is non-negative,
 * which keeps the LazyColumn's item indices and week indices the same number.
 */
private val WEEK_INDEX_EPOCH = LocalDate(1900, 1, 1)
private val WEEK_INDEX_END = LocalDate(2100, 12, 31)

internal fun weekIndexOf(date: LocalDate, weekStart: DayOfWeek): Int {
    val base = WEEK_INDEX_EPOCH.startOfGridWeek(weekStart)
    return base.daysUntil(date.startOfGridWeek(weekStart)) / 7
}

internal fun weekStartForIndex(index: Int, weekStart: DayOfWeek): LocalDate =
    WEEK_INDEX_EPOCH.startOfGridWeek(weekStart).plus(index * 7, DateTimeUnit.DAY)

/** Total weeks the continuous grid scrolls through (1900 → 2100). */
internal fun continuousWeekCount(weekStart: DayOfWeek): Int =
    weekIndexOf(WEEK_INDEX_END, weekStart) + 1

internal fun LocalDate.startOfGridWeek(weekStart: DayOfWeek): LocalDate {
    // DayOfWeek.ordinal: MONDAY=0..SUNDAY=6 → identical to ISO ordering.
    val offset = ((dayOfWeek.ordinal - weekStart.ordinal) + 7) % 7
    return minus(offset, DateTimeUnit.DAY)
}
