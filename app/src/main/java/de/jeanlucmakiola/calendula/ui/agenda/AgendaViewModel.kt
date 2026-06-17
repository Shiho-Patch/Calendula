package de.jeanlucmakiola.calendula.ui.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import javax.inject.Inject

/** How far ahead the agenda loads events from its anchor day. */
internal const val AGENDA_WINDOW_DAYS = 60

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AgendaViewModel @Inject constructor(
    private val repository: CalendarRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val zone = TimeZone.currentSystemDefault()

    private val todayDate: LocalDate
        get() = Clock.System.now().toLocalDateTime(zone).date

    private val _anchor = MutableStateFlow(todayDate)
    val anchor: StateFlow<LocalDate> = _anchor

    val state: StateFlow<AgendaUiState> = _anchor
        .flatMapLatest { anchor ->
            val range = agendaRange(anchor, AGENDA_WINDOW_DAYS, zone)
            combine(
                repository.calendars(),
                repository.instances(range),
            ) { calendars, instances ->
                buildState(anchor, calendars, instances)
            }
        }
        .catch { emit(AgendaUiState.Failure(FailureReason.ProviderUnavailable)) }
        .flowOn(io)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = AgendaUiState.Loading,
        )

    fun goToToday() {
        _anchor.value = todayDate
    }

    /** Jump the agenda window to start on a specific date (drawer jump-to-date). */
    fun goToDate(date: LocalDate) {
        _anchor.value = date
    }

    private fun buildState(
        anchor: LocalDate,
        calendars: List<CalendarSource>,
        instances: List<EventInstance>,
    ): AgendaUiState {
        if (calendars.isEmpty()) {
            return AgendaUiState.Failure(FailureReason.NoCalendarsConfigured)
        }
        val days = groupAgendaDays(anchor, instances, zone)
        return AgendaUiState.Success(anchor = anchor, today = todayDate, days = days)
    }
}

/** Inclusive instant range from the start of [anchor] through [days] days ahead. */
internal fun agendaRange(anchor: LocalDate, days: Int, zone: TimeZone): ClosedRange<Instant> {
    val from = anchor.atStartOfDayIn(zone)
    val to = anchor.plus(days, DateTimeUnit.DAY).atTime(23, 59, 59).toInstant(zone)
    return from..to
}
