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
                    buildState(ym, calendars, instances)
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

    private fun buildState(
        ym: YearMonth,
        calendars: List<CalendarSource>,
        instances: List<EventInstance>,
    ): MonthUiState {
        if (calendars.isEmpty()) {
            return MonthUiState.Failure(FailureReason.NoCalendarsConfigured)
        }
        val byDay = instances.groupBy { it.start.toLocalDateTime(zone).date }
            .mapValues { (_, evs) ->
                DayCellData(
                    count = evs.size,
                    swatches = evs.map { it.color }.distinct().take(3),
                )
            }
        return MonthUiState.Success(
            month = ym,
            today = todayDate,
            cells = byDay,
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
