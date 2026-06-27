package de.jeanlucmakiola.calendula.ui.agenda

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
import java.util.Locale
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AgendaViewModel @Inject constructor(
    private val repository: CalendarRepository,
    settingsPrefs: SettingsPrefs,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val screenRange = settingsPrefs.agendaScreenRange

    // First day of the week, for the calendar-aligned "this week" range.
    private val weekStartDay = settingsPrefs.weekStart
        .map { it.resolveFirstDay(Locale.getDefault()) }

    private val zone = TimeZone.currentSystemDefault()

    private val todayDate: LocalDate
        get() = Clock.System.now().toLocalDateTime(zone).date

    private val _anchor = MutableStateFlow(todayDate)
    val anchor: StateFlow<LocalDate> = _anchor

    // A transient, in-view override of the saved agenda range. Held in memory
    // (not persisted), so it survives view switches and rotation within the
    // session but resets to the saved default when the app is relaunched.
    private val _rangeOverride = MutableStateFlow<AgendaRange?>(null)

    val state: StateFlow<AgendaUiState> =
        combine(_anchor, screenRange, _rangeOverride, weekStartDay) { anchor, default, override, weekStart ->
            AgendaParams(
                anchor = anchor,
                range = override ?: default,
                rangeIsOverride = override != null && override != default,
                weekStart = weekStart,
            )
        }
        .flatMapLatest { params ->
            val window = agendaRange(
                params.anchor,
                params.range.dayCount(params.anchor, params.weekStart) - 1,
                zone,
            )
            combine(
                repository.calendars(),
                repository.instances(window),
            ) { calendars, instances ->
                buildState(params, calendars, instances)
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

    /** Temporarily override the agenda range for this session (the bottom-left pill). */
    fun setRangeOverride(range: AgendaRange) {
        _rangeOverride.value = range
    }

    private data class AgendaParams(
        val anchor: LocalDate,
        val range: AgendaRange,
        val rangeIsOverride: Boolean,
        val weekStart: DayOfWeek,
    )

    private fun buildState(
        params: AgendaParams,
        calendars: List<CalendarSource>,
        instances: List<EventInstance>,
    ): AgendaUiState {
        if (calendars.isEmpty()) {
            return AgendaUiState.Failure(FailureReason.NoCalendarsConfigured)
        }
        val anchor = params.anchor
        val days = groupAgendaDays(anchor, instances, zone)
        val rangeEnd = anchor.plus(
            params.range.dayCount(anchor, params.weekStart) - 1,
            DateTimeUnit.DAY,
        )
        return AgendaUiState.Success(
            anchor = anchor,
            today = todayDate,
            days = days,
            range = params.range,
            rangeIsOverride = params.rangeIsOverride,
            rangeEnd = rangeEnd,
        )
    }
}

/** Inclusive instant range from the start of [anchor] through [days] days ahead. */
internal fun agendaRange(anchor: LocalDate, days: Int, zone: TimeZone): ClosedRange<Instant> {
    val from = anchor.atStartOfDayIn(zone)
    val to = anchor.plus(days, DateTimeUnit.DAY).atTime(23, 59, 59).toInstant(zone)
    return from..to
}
