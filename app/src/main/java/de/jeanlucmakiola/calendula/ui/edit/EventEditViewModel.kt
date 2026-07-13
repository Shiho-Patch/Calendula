package de.jeanlucmakiola.calendula.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.calendar.NoSuchEventException
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.data.prefs.resolveDefaultReminder
import de.jeanlucmakiola.calendula.domain.AccessLevel
import de.jeanlucmakiola.calendula.domain.Availability
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EditSnapshot
import de.jeanlucmakiola.calendula.domain.EventAttendee
import de.jeanlucmakiola.calendula.domain.EventColorOption
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventFormField
import de.jeanlucmakiola.calendula.domain.RecurringWriteScope
import de.jeanlucmakiola.calendula.domain.populatedFields
import de.jeanlucmakiola.calendula.domain.problems
import de.jeanlucmakiola.calendula.domain.toEditSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
    // True once the user has hand-edited the reminders on a new event, which
    // freezes the auto-applied default: switching calendars no longer overwrites
    // their choice. Reset with the form.
    private val _remindersTouched = MutableStateFlow(false)

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

    private data class ReminderDefaults(
        val timed: List<Int>,
        val allDay: List<Int>,
        val timedOverrides: Map<Long, List<Int>>,
        val allDayOverrides: Map<Long, List<Int>>,
    )

    private data class ExternalInputs(
        val writable: List<CalendarSource>,
        val lastUsed: Long?,
        val defaultFields: Set<EventFormField>,
        val allowColorOnUnsupported: Boolean,
        val autofocusTitle: Boolean,
    )

    /** Every calendar the provider exposes; the source for both lists below. */
    private val allCalendars: Flow<List<CalendarSource>> =
        repository.calendars().catch { emit(emptyList()) }

    /**
     * Writable calendars — the only valid event targets. Disabled calendars are
     * excluded, so you can't create into a calendar you've removed from the app;
     * a last-used preselect landing on a now-disabled calendar falls back to the
     * first remaining writable one (handled by [resolvedCalendarId] and [state]).
     * Managed special-dates calendars are excluded too: their events are owned by
     * the contact sync, which would delete any user event created there.
     */
    private val writableCalendars: Flow<List<CalendarSource>> = combine(
        allCalendars,
        prefs.disabledCalendarIds,
    ) { calendars, disabled ->
        calendars.filter { it.canModifyContents && it.id !in disabled && !it.isManaged }
    }

    /** The target calendar id, resolved exactly as the form shows it. */
    private val resolvedCalendarId: Flow<Long?> = combine(
        _form.map { it?.calendarId },
        writableCalendars,
        prefs.lastUsedCalendarId,
    ) { picked, writable, lastUsed ->
        picked
            ?: lastUsed?.takeIf { id -> writable.any { it.id == id } }
            ?: writable.firstOrNull()?.id
    }.distinctUntilChanged()

    /** The resolved calendar's published event palette, refetched when it changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val colorPalette: Flow<List<EventColorOption>> = resolvedCalendarId
        .flatMapLatest { id ->
            flow { emit(id?.let { repository.eventColorPalette(it) }.orEmpty()) }
        }
        .flowOn(io)

    val state: StateFlow<EventEditUiState?> = combine(
        combine(_form, _saveState, _showProblems, _revealed, _editTarget, ::LocalInputs),
        combine(
            writableCalendars,
            prefs.lastUsedCalendarId,
            settingsPrefs.defaultFormFields,
            settingsPrefs.allowColorOnUnsupportedCalendars,
            settingsPrefs.autofocusEventTitle,
            ::ExternalInputs,
        ).flowOn(io),
        colorPalette,
        allCalendars,
    ) { local, external, palette, allCalendars ->
        val form = local.form ?: return@combine null
        val resolvedId = form.calendarId
            ?: external.lastUsed?.takeIf { id -> external.writable.any { it.id == id } }
            ?: external.writable.firstOrNull()?.id
        val resolved = form.copy(calendarId = resolvedId)
        val resolvedCalendar = allCalendars.firstOrNull { it.id == resolvedId }
        // Editing an event in a managed calendar locks its synced fields. Keyed
        // off the calendar's durable marker, not a stored id, so it holds after a
        // backup restore too.
        val isManaged = local.editTarget != null && resolvedCalendar?.isManaged == true
        // The picker offers writable calendars only; when editing a managed event
        // its own (excluded) calendar is added back so the row still names it.
        val pickerCalendars =
            if (isManaged && resolvedCalendar != null) external.writable + resolvedCalendar
            else external.writable
        val visibleFields = external.defaultFields + local.revealed
        EventEditUiState(
            form = resolved,
            calendars = pickerCalendars,
            problems = if (local.showProblems) resolved.problems() else emptySet(),
            saveState = local.saveState,
            visibleFields = visibleFields,
            hiddenFields = (EventFormField.entries.toSet() - visibleFields).sorted(),
            isEditing = local.editTarget != null,
            isManaged = isManaged,
            autofocusTitle = external.autofocusTitle,
            // A modified-occurrence exception can't carry its own rule, so
            // the scope dialog drops "only this event" after a rule change.
            recurrenceChanged = local.editTarget != null &&
                resolved.rrule != local.editTarget.original.rrule,
            colorPalette = palette,
            allowColorOnUnsupportedCalendars = external.allowColorOnUnsupported,
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
        applyDefaultReminder()
    }

    /**
     * Seed a fresh event from a parsed `.ics` file (the single-event "open into
     * the create form" path). [form] already carries the file's fields; its
     * [EventForm.calendarId] is null so the calendar still resolves to the
     * last-used/first-writable one, and reminders are frozen as touched so the
     * settings default never overwrites what the file specified. No-op when a
     * form is already open, so the prefill survives configuration changes.
     */
    fun openImported(form: EventForm) {
        if (_form.value != null || _editTarget.value != null) return
        _remindersTouched.value = true
        _revealed.value = form.populatedFields()
        _form.value = form
    }

    /**
     * Prefill a new event's reminders from the settings default — the all-day
     * default for all-day events, otherwise the resolved calendar's per-calendar
     * override or the global timed default. No-op while editing an existing event
     * or once the user has hand-edited the reminders, so the auto-default never
     * clobbers a manual choice. [calendarId] short-circuits the resolution after a
     * calendar switch; null resolves it as the form does.
     */
    private fun applyDefaultReminder(calendarId: Long? = null) {
        if (_editTarget.value != null || _remindersTouched.value) return
        viewModelScope.launch {
            val defaults = combine(
                settingsPrefs.defaultReminderMinutes,
                settingsPrefs.defaultAllDayReminderMinutes,
                settingsPrefs.perCalendarReminderOverride,
                settingsPrefs.perCalendarAllDayReminderOverride,
            ) { timed, allDay, timedOv, allDayOv ->
                ReminderDefaults(timed, allDay, timedOv, allDayOv)
            }.first()
            val targetId = calendarId ?: resolvedCalendarId.first()
            // Re-check after suspending: bail if the form closed or the user edited.
            val form = _form.value ?: return@launch
            if (_editTarget.value != null || _remindersTouched.value) return@launch
            val reminders = resolveDefaultReminder(
                timedGlobal = defaults.timed,
                allDayGlobal = defaults.allDay,
                timedOverrides = defaults.timedOverrides,
                allDayOverrides = defaults.allDayOverrides,
                calendarId = targetId,
                isAllDay = form.isAllDay,
            )
            _form.value = form.copy(reminders = reminders)
            // Surface the section so an auto-applied default is visible and
            // removable, even when Reminders isn't a default-shown field.
            if (reminders.isNotEmpty()) {
                _revealed.value = _revealed.value + EventFormField.Reminders
            }
        }
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
        _remindersTouched.value = false
    }

    /** Unfold one optional field, picked in the "more fields" dialog. */
    fun revealField(field: EventFormField) {
        _revealed.value = _revealed.value + field
    }

    // The title field wraps (multi-line) so long titles stay visible (#33), but
    // a title is one logical line: drop any newline the IME's Enter key or a
    // paste would introduce, so it never reaches the provider's TITLE column.
    fun setTitle(value: String) =
        update { it.copy(title = value.replace("\n", "").replace("\r", "")) }
    fun setLocation(value: String) = update { it.copy(location = value) }
    fun setDescription(value: String) = update { it.copy(description = value) }
    fun setAllDay(value: Boolean) {
        update { it.copy(isAllDay = value) }
        // The default reminder differs for all-day vs timed; re-apply the
        // type-appropriate default unless the user has hand-edited it (guarded).
        applyDefaultReminder()
    }

    /**
     * Switching calendars drops any chosen colour: a palette key is
     * account-scoped, and a raw colour may be invalid on the new calendar.
     * The event falls back to the new calendar's colour until re-picked.
     */
    fun setCalendar(id: Long) {
        update { it.copy(calendarId = id, colorKey = null, color = null) }
        // A fresh event re-inherits the new calendar's default reminder unless
        // the user has already hand-edited it (guarded inside).
        applyDefaultReminder(id)
    }
    fun setAvailability(value: Availability) = update { it.copy(availability = value) }
    fun setAccessLevel(value: AccessLevel) = update { it.copy(accessLevel = value) }

    /** Pick a palette colour (round-trips via its key); [argb] is its swatch. */
    fun setColorKey(key: String, argb: Int) = update { it.copy(colorKey = key, color = argb) }

    /** Pick a raw colour (local / opted-in calendars), clearing any palette key. */
    fun setColorRaw(argb: Int) = update { it.copy(colorKey = null, color = argb) }

    /** Clear the colour so the event inherits its calendar's. */
    fun clearColor() = update { it.copy(colorKey = null, color = null) }

    /** Bare RRULE value from the recurrence picker; null = does not repeat. */
    fun setRecurrence(rrule: String?) = update { it.copy(rrule = rrule) }

    fun addReminder(minutes: Int) {
        _remindersTouched.value = true
        update { it.copy(reminders = (it.reminders + minutes).distinct().sorted()) }
    }

    fun removeReminder(minutes: Int) {
        _remindersTouched.value = true
        update { it.copy(reminders = it.reminders - minutes) }
    }

    /**
     * Add a guest, keyed on a trimmed lower-cased email so re-adding the same
     * address just refreshes it (no duplicate row). A blank email is ignored —
     * the email is what we write and dedup on.
     */
    fun addAttendee(email: String, name: String = "", optional: Boolean = false) {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return
        update { form ->
            val attendee = EventAttendee(email = trimmed, name = name.trim(), optional = optional)
            val rest = form.attendees.filterNot { it.email.equals(trimmed, ignoreCase = true) }
            form.copy(attendees = rest + attendee)
        }
    }

    fun removeAttendee(attendee: EventAttendee) =
        update { it.copy(attendees = it.attendees.filterNot { a -> a.email.equals(attendee.email, ignoreCase = true) }) }

    /** Flip a guest between required and optional (the per-row role toggle). */
    fun setAttendeeOptional(attendee: EventAttendee, optional: Boolean) = update { form ->
        form.copy(
            attendees = form.attendees.map { a ->
                if (a.email.equals(attendee.email, ignoreCase = true)) a.copy(optional = optional) else a
            },
        )
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
        // Changing the calendar moves the event (copy+delete — CALENDAR_ID can't
        // be updated in place) and is inherently whole-series: an occurrence
        // can't live in a different calendar than its series, so a move skips the
        // scope dialog even for a recurring event.
        val movingCalendar = target != null && form.calendarId != target.original.calendarId
        // Managed events are a yearly series whose editable fields (reminders,
        // notes, location) live on the series row — never offer the scope dialog,
        // which would split the series into an exception the sync then reverts.
        if (target != null && target.original.rrule != null && !current.isManaged && !movingCalendar) {
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
                } else if (form.calendarId != target.original.calendarId) {
                    // Move to the picked calendar (copy+delete), carrying any
                    // field edits made in the same save. The target becomes the
                    // last-used calendar, like a create does.
                    repository.moveEvent(
                        eventId = target.eventId,
                        targetCalendarId = requireNotNull(form.calendarId),
                        original = target.original,
                        updated = form,
                    )
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
