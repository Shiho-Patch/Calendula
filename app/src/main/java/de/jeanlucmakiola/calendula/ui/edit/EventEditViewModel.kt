package de.jeanlucmakiola.calendula.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.problems
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import javax.inject.Inject

/**
 * Holds the event form being composed. The form's calendar id resolves to
 * (user pick > last used > first writable); the resolved value is what the UI
 * shows and what gets saved.
 */
@HiltViewModel
class EventEditViewModel @Inject constructor(
    private val repository: CalendarRepository,
    private val prefs: CalendarPrefs,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _form = MutableStateFlow<EventForm?>(null)
    private val _saveState = MutableStateFlow<SaveUiState>(SaveUiState.Idle)
    // Problems stay hidden until the first save attempt, so a half-filled
    // form isn't already shouting errors.
    private val _showProblems = MutableStateFlow(false)

    val state: StateFlow<EventEditUiState?> = combine(
        _form,
        repository.calendars()
            .map { calendars -> calendars.filter { it.canModifyContents } }
            .catch { emit(emptyList()) },
        prefs.lastUsedCalendarId,
        _saveState,
        _showProblems,
    ) { form, writable, lastUsed, saveState, showProblems ->
        if (form == null) return@combine null
        val resolvedId = form.calendarId
            ?: lastUsed?.takeIf { id -> writable.any { it.id == id } }
            ?: writable.firstOrNull()?.id
        val resolved = form.copy(calendarId = resolvedId)
        EventEditUiState(
            form = resolved,
            calendars = writable,
            problems = if (showProblems) resolved.problems() else emptySet(),
            saveState = saveState,
        )
    }
        .flowOn(io)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null,
        )

    /**
     * Initialise a fresh form for a new event on [date]. No-op when a form is
     * already open, so user input survives configuration changes; [reset]
     * clears it when the screen closes.
     */
    fun openNew(date: LocalDate) {
        if (_form.value != null) return
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val start = if (date == now.toLocalDateTime(zone).date) {
            // Today: the next full hour (may roll into tomorrow before midnight).
            val hourMillis = 3_600_000L
            val rounded = (now.toEpochMilliseconds() / hourMillis + 1) * hourMillis
            Instant.fromEpochMilliseconds(rounded).toLocalDateTime(zone)
        } else {
            LocalDateTime(date, LocalTime(9, 0))
        }
        val end = (start.toInstant(zone) + 1.hours).toLocalDateTime(zone)
        _form.value = EventForm(calendarId = null, start = start, end = end)
    }

    /** Forget the open form; the next [openNew] starts clean. */
    fun reset() {
        _form.value = null
        _saveState.value = SaveUiState.Idle
        _showProblems.value = false
    }

    fun setTitle(value: String) = update { it.copy(title = value) }
    fun setLocation(value: String) = update { it.copy(location = value) }
    fun setDescription(value: String) = update { it.copy(description = value) }
    fun setAllDay(value: Boolean) = update { it.copy(isAllDay = value) }
    fun setCalendar(id: Long) = update { it.copy(calendarId = id) }

    /** Moving the start drags the end along, preserving the duration. */
    fun setStartDate(date: LocalDate) = moveStart { LocalDateTime(date, it.time) }
    fun setStartTime(time: LocalTime) = moveStart { LocalDateTime(it.date, time) }
    fun setEndDate(date: LocalDate) = update { it.copy(end = LocalDateTime(date, it.end.time)) }
    fun setEndTime(time: LocalTime) = update { it.copy(end = LocalDateTime(it.end.date, time)) }

    /** Validate and write. Terminal results land in [saveState]. */
    fun save() {
        val current = state.value ?: return
        if (current.saveState == SaveUiState.Saving) return
        val form = current.form
        if (form.problems().isNotEmpty()) {
            _showProblems.value = true
            return
        }
        viewModelScope.launch {
            _saveState.value = SaveUiState.Saving
            _saveState.value = try {
                repository.createEvent(form)
                prefs.setLastUsedCalendarId(requireNotNull(form.calendarId))
                SaveUiState.Saved
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                SaveUiState.NeedsPermission
            } catch (e: Exception) {
                SaveUiState.Failed
            }
        }
    }

    /** Reset [saveState] after the screen handled a terminal result. */
    fun consumeSaveResult() {
        _saveState.value = SaveUiState.Idle
    }

    private fun moveStart(transform: (LocalDateTime) -> LocalDateTime) = update { form ->
        val zone = TimeZone.currentSystemDefault()
        val newStart = transform(form.start)
        val duration = form.end.toInstant(zone) - form.start.toInstant(zone)
        val newEnd = (newStart.toInstant(zone) + duration).toLocalDateTime(zone)
        form.copy(start = newStart, end = newEnd)
    }

    private inline fun update(block: (EventForm) -> EventForm) {
        _form.value = _form.value?.let(block)
    }
}
