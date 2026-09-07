package de.jeanlucmakiola.calendula.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.FailureReason
import de.jeanlucmakiola.calendula.domain.calendarListFailure
import de.jeanlucmakiola.calendula.ui.week.layoutAllDay
import de.jeanlucmakiola.calendula.ui.week.layoutDay
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DayViewModel @Inject constructor(
    private val repository: CalendarRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val zone = TimeZone.currentSystemDefault()

    private val todayDate: LocalDate
        get() = Clock.System.now().toLocalDateTime(zone).date

    private val _date = MutableStateFlow(todayDate)
    val date: StateFlow<LocalDate> = _date

    val state: StateFlow<DayUiState> = _date
        .flatMapLatest { day ->
            val range = dayRange(day, zone)
            combine(
                repository.calendars(),
                repository.instances(range),
            ) { calendars, instances ->
                buildState(day, calendars, instances)
            }
        }
        .catch { emit(DayUiState.Failure(FailureReason.ProviderUnavailable)) }
        .flowOn(io)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = DayUiState.Loading,
        )

    fun goToPrev() {
        _date.value = _date.value.minus(1, DateTimeUnit.DAY)
    }

    fun goToNext() {
        _date.value = _date.value.plus(1, DateTimeUnit.DAY)
    }

    fun goToToday() {
        _date.value = todayDate
    }

    /** Jump to a specific date (e.g. when opened from the month grid). */
    fun goToDate(date: LocalDate) {
        _date.value = date
    }

    private fun buildState(
        day: LocalDate,
        calendars: List<CalendarSource>,
        instances: List<EventInstance>,
    ): DayUiState {
        calendarListFailure(calendars)?.let { return DayUiState.Failure(it) }
        val days = listOf(day)
        val allDay = instances.filter { it.isAllDay }
        val timed = instances.filterNot { it.isAllDay }
        return DayUiState.Success(
            date = day,
            today = todayDate,
            allDay = layoutAllDay(allDay, days, zone),
            timed = layoutDay(timed, day, zone),
        )
    }
}

/** Half-open instant range covering the single calendar [date]. */
internal fun dayRange(date: LocalDate, zone: TimeZone): ClosedRange<Instant> {
    val from = date.atStartOfDayIn(zone)
    val to = date.atTime(23, 59, 59).toInstant(zone)
    return from..to
}
