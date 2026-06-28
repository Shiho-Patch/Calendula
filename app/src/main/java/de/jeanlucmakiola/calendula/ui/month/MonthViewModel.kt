package de.jeanlucmakiola.calendula.ui.month

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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.Locale
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
    private val locale: Locale = Locale.getDefault()

    /** First day of the week, from the Settings preference (AUTO → locale). */
    val weekStart: StateFlow<DayOfWeek> = settingsPrefs.weekStart
        .map { it.resolveFirstDay(locale) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = DayOfWeek.MONDAY,
        )

    /** Whether to fade events that have already finished (display concern only). */
    val dimCompletedEvents: StateFlow<Boolean> = settingsPrefs.dimCompletedEvents
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = false,
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
        return MonthUiState.Success(
            month = ym,
            today = todayDate,
            weeks = layoutMonthWeeks(ym, weekStart, instances, zone),
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
        MonthWeek(
            days = days,
            spans = spans,
            timedByDay = days.associateWith { d ->
                singles.filter { it.coversDay(d, zone) }.sortedBy { it.start }
            },
            countByDay = days.associateWith { d -> weekEvents.count { it.coversDay(d, zone) } },
        )
    }
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

internal fun LocalDate.startOfGridWeek(weekStart: DayOfWeek): LocalDate {
    // DayOfWeek.ordinal: MONDAY=0..SUNDAY=6 → identical to ISO ordering.
    val offset = ((dayOfWeek.ordinal - weekStart.ordinal) + 7) % 7
    return minus(offset, DateTimeUnit.DAY)
}
