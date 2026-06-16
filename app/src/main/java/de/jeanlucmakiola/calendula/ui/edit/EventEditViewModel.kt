package de.jeanlucmakiola.calendula.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.calendar.NoSuchEventException
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.domain.AccessLevel
import de.jeanlucmakiola.calendula.domain.Availability
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EditSnapshot
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventFormField
import de.jeanlucmakiola.calendula.domain.RecurringWriteScope
import de.jeanlucmakiola.calendula.domain.populatedFields
import de.jeanlucmakiola.calendula.domain.problems
import de.jeanlucmakiola.calendula.domain.toEditSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val settingsPrefs: SettingsPrefs,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _form = MutableStateFlow<EventForm?>(null)
    private val _saveState = MutableStateFlow<SaveUiState>(SaveUiState.Idle)
    // Problems stay hidden until the first save attempt, so a half-filled
    // form isn't already shouting errors.
    private val _showProblems = MutableStateFlow(false)
    // Fields added through the "more fields" picker; folds back on reset().
    // openForEdit seeds it with the sections that already hold values.
    private val _revealed = MutableStateFlow<Set<EventFormField>>(emptySet())
    // Set while the form edits an existing event instead of composing a new one.
    private val _editTarget = MutableStateFlow<EditTarget?>(null)
    private val _loadFailed = MutableStateFlow(false)

    /** True when the event to edit couldn't be loaded; the screen closes itself. */
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    /**
     * The event being edited plus everything the form saw at load time.
     * For recurring events the write scope is chosen at save time; the
     * tapped occurrence's [beginMillis]/[endMillis] anchor occurrence-level
     * writes and the conflict re-read. [zone] is pinned at load so a device
     * timezone change mid-edit can't fake a conflict.
     */
    private data class EditTarget(
        val eventId: Long,
        val snapshot: EditSnapshot,
        val beginMillis: Long,
        val endMillis: Long,
        val zone: TimeZone,
    ) {
        val original: EventForm get() = snapshot.form
    }

    private data class LocalInputs(
        val form: EventForm?,
        val saveState: SaveUiState,
        val showProblems: Boolean,
        val revealed: Set<EventFormField>,
        val editTarget: EditTarget?,
    )

    private data class ExternalInputs(
        val writable: List<CalendarSource>,
        val lastUsed: Long?,
        val defaultFields: Set<EventFormField>,
    )

    val state: StateFlow<EventEditUiState?> = combine(
        combine(_form, _saveState, _showProblems, _revealed, _editTarget, ::LocalInputs),
        combine(
            repository.calendars()
                .map { calendars -> calendars.filter { it.canModifyContents } }
                .catch { emit(emptyList()) },
            prefs.lastUsedCalendarId,
            settingsPrefs.defaultFormFields,
            ::ExternalInputs,
        ).flowOn(io),
    ) { local, external ->
        val form = local.form ?: return@combine null
        val resolvedId = form.calendarId
            ?: external.lastUsed?.takeIf { id -> external.writable.any { it.id == id } }
            ?: external.writable.firstOrNull()?.id
        val resolved = form.copy(calendarId = resolvedId)
        val visibleFields = external.defaultFields + local.revealed
        EventEditUiState(
            form = resolved,
            calendars = external.writable,
            problems = if (local.showProblems) resolved.problems() else emptySet(),
            saveState = local.saveState,
            visibleFields = visibleFields,
            hiddenFields = (EventFormField.entries.toSet() - visibleFields).sorted(),
            isEditing = local.editTarget != null,
            // A modified-occurrence exception can't carry its own rule, so
            // the scope dialog drops "only this event" after a rule change.
            recurrenceChanged = local.editTarget != null &&
                resolved.rrule != local.editTarget.original.rrule,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null,
        )

    /**
     * Initialise a fresh form for a new event on [date]. [startMinutes] (minutes
     * from midnight) anchors the start when the form is opened by tapping a slot
     * in the day/week grid; without it the default is the next full hour (today)
     * or 09:00 (any other day). No-op when a form is already open, so user input
     * survives configuration changes; [reset] clears it when the screen closes.
     */
    fun openNew(date: LocalDate, startMinutes: Int? = null) {
        if (_form.value != null) return
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val start = when {
            startMinutes != null ->
                LocalDateTime(date, LocalTime(startMinutes / 60, startMinutes % 60))
            date == now.toLocalDateTime(zone).date -> {
                // Today: the next full hour (may roll into tomorrow before midnight).
                val hourMillis = 3_600_000L
                val rounded = (now.toEpochMilliseconds() / hourMillis + 1) * hourMillis
                Instant.fromEpochMilliseconds(rounded).toLocalDateTime(zone)
            }
            else -> LocalDateTime(date, LocalTime(9, 0))
        }
        val end = (start.toInstant(zone) + 1.hours).toLocalDateTime(zone)
        _form.value = EventForm(calendarId = null, start = start, end = end)
    }

    /**
     * Load an existing event into the form. [beginMillis]/[endMillis] are the
     * tapped occurrence's own times, like on the detail screen. No-op while a
     * form is open, so user edits survive configuration changes.
     */
    fun openForEdit(eventId: Long, beginMillis: Long, endMillis: Long) {
        if (_form.value != null || _editTarget.value != null) return
        viewModelScope.launch {
            val detail = try {
                repository.eventDetail(eventId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _loadFailed.value = true
                return@launch
            }
            val zone = TimeZone.currentSystemDefault()
            val snapshot = detail.toEditSnapshot(beginMillis, endMillis, zone)
            _editTarget.value = EditTarget(eventId, snapshot, beginMillis, endMillis, zone)
            // Sections holding data must show even when not in the defaults.
            _revealed.value = snapshot.form.populatedFields()
            _form.value = snapshot.form
        }
    }

    /** Forget the open form; the next [openNew]/[openForEdit] starts clean. */
    fun reset() {
        _form.value = null
        _saveState.value = SaveUiState.Idle
        _showProblems.value = false
        _revealed.value = emptySet()
        _editTarget.value = null
        _loadFailed.value = false
    }

    /** Unfold one optional field, picked in the "more fields" dialog. */
    fun revealField(field: EventFormField) {
        _revealed.value = _revealed.value + field
    }

    fun setTitle(value: String) = update { it.copy(title = value) }
    fun setLocation(value: String) = update { it.copy(location = value) }
    fun setDescription(value: String) = update { it.copy(description = value) }
    fun setAllDay(value: Boolean) = update { it.copy(isAllDay = value) }
    fun setCalendar(id: Long) = update { it.copy(calendarId = id) }
    fun setAvailability(value: Availability) = update { it.copy(availability = value) }
    fun setAccessLevel(value: AccessLevel) = update { it.copy(accessLevel = value) }

    /** Bare RRULE value from the recurrence picker; null = does not repeat. */
    fun setRecurrence(rrule: String?) = update { it.copy(rrule = rrule) }

    fun addReminder(minutes: Int) = update {
        it.copy(reminders = (it.reminders + minutes).distinct().sorted())
    }

    fun removeReminder(minutes: Int) = update {
        it.copy(reminders = it.reminders - minutes)
    }

    /** Moving the start drags the end along, preserving the duration. */
    fun setStartDate(date: LocalDate) = moveStart { LocalDateTime(date, it.time) }
    fun setStartTime(time: LocalTime) = moveStart { LocalDateTime(it.date, time) }
    fun setEndDate(date: LocalDate) = update { it.copy(end = LocalDateTime(date, it.end.time)) }
    fun setEndTime(time: LocalTime) = update { it.copy(end = LocalDateTime(it.end.date, time)) }

    /**
     * Validate and write. Saving a dirty recurring event pauses in
     * [SaveUiState.AwaitingScope] until the screen answers via
     * [saveWithScope]; everything else writes directly. Terminal results
     * land in [saveState].
     */
    fun save() {
        val current = state.value ?: return
        if (current.saveState == SaveUiState.Saving) return
        val form = current.form
        if (form.problems().isNotEmpty()) {
            _showProblems.value = true
            return
        }
        val target = _editTarget.value
        if (target != null && form == target.original) {
            // A pristine form saves as a no-op instead of a write.
            _saveState.value = SaveUiState.Saved
            return
        }
        if (target != null && target.original.rrule != null) {
            _saveState.value = SaveUiState.AwaitingScope
            return
        }
        performSave(form, RecurringWriteScope.AllEvents)
    }

    /** Finish a save parked in [SaveUiState.AwaitingScope]. */
    fun saveWithScope(scope: RecurringWriteScope) {
        val current = state.value ?: return
        if (current.saveState != SaveUiState.AwaitingScope) return
        performSave(current.form, scope)
    }

    /** Finish a save parked in [SaveUiState.AwaitingConflict], overwriting. */
    fun saveOverwriting() {
        val current = state.value ?: return
        val parked = current.saveState as? SaveUiState.AwaitingConflict ?: return
        performSave(current.form, parked.scope, ignoreConflict = true)
    }

    private fun performSave(
        form: EventForm,
        scope: RecurringWriteScope,
        ignoreConflict: Boolean = false,
    ) {
        val target = _editTarget.value
        viewModelScope.launch {
            _saveState.value = SaveUiState.Saving
            // No locking (plan 03, decision 5): right before writing, re-read
            // the event and compare against what the form loaded. An external
            // change parks the save in a conflict dialog instead of silently
            // clobbering the edited fields.
            if (target != null && !ignoreConflict) {
                val fresh = try {
                    repository.eventDetail(target.eventId)
                        .toEditSnapshot(target.beginMillis, target.endMillis, target.zone)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: NoSuchEventException) {
                    _saveState.value = SaveUiState.Gone
                    return@launch
                } catch (e: Exception) {
                    // Can't verify — proceed; a real problem fails the write itself.
                    null
                }
                if (fresh != null && fresh != target.snapshot) {
                    _saveState.value = SaveUiState.AwaitingConflict(scope)
                    return@launch
                }
            }
            _saveState.value = try {
                if (target == null) {
                    repository.createEvent(form)
                    prefs.setLastUsedCalendarId(requireNotNull(form.calendarId))
                } else {
                    when (scope) {
                        RecurringWriteScope.ThisEvent ->
                            repository.updateOccurrence(target.eventId, target.beginMillis, form)
                        RecurringWriteScope.ThisAndFollowing ->
                            repository.updateEventFromOccurrence(
                                eventId = target.eventId,
                                beginMillis = target.beginMillis,
                                original = target.original,
                                updated = form,
                            )
                        RecurringWriteScope.AllEvents ->
                            repository.updateEvent(target.eventId, target.original, form)
                    }
                }
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
