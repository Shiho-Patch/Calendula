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
import de.jeanlucmakiola.calendula.ui.common.PageStateCache
import de.jeanlucmakiola.calendula.ui.week.layoutAllDay
import de.jeanlucmakiola.calendula.ui.week.layoutDay
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import javax.inject.Inject
import kotlin.math.abs

/** How far from the requested day [DayViewModel.day] keeps other days cached. */
private const val DAY_CACHE_DAYS = 7

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

    /** The pager page the anchor day sits on. */
    val anchorPage: StateFlow<Int> = _date
        .map { dayPageFor(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = dayPageFor(todayDate),
        )

    /**
     * The anchor day's state. Once loaded it never falls back to [DayUiState.Loading]:
     * moving the anchor keeps the last result until the new day arrives, since
     * the pages draw their own days and this only gates the failure screen.
     */
    val state: StateFlow<DayUiState> = _date
        .flatMapLatest { date -> day(date).filterNot { it is DayUiState.Loading } }
        .catch { emit(DayUiState.Failure(FailureReason.ProviderUnavailable)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = DayUiState.Loading,
        )

    private val days = PageStateCache<LocalDate, DayUiState>(
        scope = viewModelScope,
        initial = DayUiState.Loading,
        // Far enough to cover the pages either side and a swipe back, so paging
        // through a month doesn't keep a month of queries around.
        keep = { cached, requested -> abs(cached.daysUntil(requested)) <= DAY_CACHE_DAYS },
    ) { date ->
        combine(
            repository.calendars(),
            repository.instances(dayRange(date, zone)),
        ) { calendars, instances ->
            buildState(date, calendars, instances)
        }
            .catch { emit(DayUiState.Failure(FailureReason.ProviderUnavailable)) }
            .flowOn(io)
    }

    /** The day [date], shared between its pager page and [state]. Main thread only. */
    fun day(date: LocalDate): StateFlow<DayUiState> = days.get(date)

    /** The pager came to rest on [page]; follow it unless it is already the anchor's. */
    fun onPageSettled(page: Int) {
        if (dayPageFor(_date.value) != page) _date.value = dayForPage(page)
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
