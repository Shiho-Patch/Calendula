package de.jeanlucmakiola.calendula.ui.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.data.prefs.PastEventDisplay
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

    // The saved agenda range plus the range-bar visibility toggle.
    private val agendaSettings = combine(
        settingsPrefs.agendaScreenRange,
        settingsPrefs.agendaShowRangeBar,
    ) { range, showBar -> AgendaSettings(range, showBar) }

    // First day of the week, for the calendar-aligned "this week" range.
    private val weekStartDay = settingsPrefs.weekStart
        .map { it.resolveFirstDay(Locale.getDefault()) }

    /**
     * How to treat events that already ended today (show / dim / hide). A display
     * concern only, so it rides alongside the data state rather than re-querying;
     * the screen combines it with a per-minute "now" to fade or drop past rows.
     */
    val pastEventDisplay: StateFlow<PastEventDisplay> = settingsPrefs.pastEventDisplay
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = PastEventDisplay.SHOW,
        )

    /**
     * Whether to keep today anchored at the top with a "nothing left today"
     * placeholder even once it has no remaining events (#35). A display concern
     * applied in the composition (after past-event filtering), so it rides
     * alongside the data state like [pastEventDisplay] rather than re-querying.
     */
    val showToday: StateFlow<Boolean> = settingsPrefs.agendaShowToday
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = true,
        )

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
        combine(_anchor, agendaSettings, _rangeOverride, weekStartDay) { anchor, settings, override, weekStart ->
            AgendaParams(
                anchor = anchor,
                range = override ?: settings.range,
                rangeIsOverride = override != null && override != settings.range,
                weekStart = weekStart,
                showRangeBar = settings.showBar,
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

    private data class AgendaSettings(
        val range: AgendaRange,
        val showBar: Boolean,
    )

    private data class AgendaParams(
        val anchor: LocalDate,
        val range: AgendaRange,
        val rangeIsOverride: Boolean,
        val weekStart: DayOfWeek,
        val showRangeBar: Boolean,
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
            showRangeBar = params.showRangeBar,
        )
    }
}

/** Inclusive instant range from the start of [anchor] through [days] days ahead. */
internal fun agendaRange(anchor: LocalDate, days: Int, zone: TimeZone): ClosedRange<Instant> {
    val from = anchor.atStartOfDayIn(zone)
    val to = anchor.plus(days, DateTimeUnit.DAY).atTime(23, 59, 59).toInstant(zone)
    return from..to
}
