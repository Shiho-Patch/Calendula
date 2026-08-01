package de.jeanlucmakiola.calendula.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.calendar.NoSuchEventException
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventFormProblem
import de.jeanlucmakiola.calendula.domain.RecurringWriteScope
import de.jeanlucmakiola.calendula.domain.allowsEventMove
import de.jeanlucmakiola.calendula.domain.problems
import de.jeanlucmakiola.calendula.domain.realignRecurrence
import de.jeanlucmakiola.calendula.domain.resolvedZone
import de.jeanlucmakiola.calendula.domain.shiftedByDays
import de.jeanlucmakiola.calendula.domain.shiftedTo
import de.jeanlucmakiola.calendula.domain.toEditForm
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import javax.inject.Inject

/** Where a dragged event should land. */
sealed interface MoveTarget {
    /** A new start instant — a timeline drag, which moves time and day at once. */
    data class Start(val instant: Instant) : MoveTarget

    /** A new first day, keeping the time of day — a month-grid or all-day drag. */
    data class Day(val date: LocalDate) : MoveTarget
}

/**
 * One dropped event. [beginMillis]/[endMillis] are the dragged *occurrence's*
 * own times (`Instances.BEGIN`/`END`), exactly as the detail and edit screens
 * pass them, so a recurring series resolves the right occurrence.
 */
data class MoveRequest(
    val eventId: Long,
    val beginMillis: Long,
    val endMillis: Long,
    val target: MoveTarget,
)

/**
 * A recurring drop waiting for the user to pick how far it reaches.
 * [occurrenceOnly] means the rule names days that a single moved occurrence
 * can't re-derive (`BYDAY=MO,WE`, `2TH`, …), so the only honest option left is
 * this one occurrence — see [realignRecurrence].
 */
data class MoveScopePrompt(val occurrenceOnly: Boolean)

/** The inverse of a completed move, for the undo action. */
data class MoveUndo(
    val eventId: Long,
    /** The form as it now stands (the moved state). */
    val moved: EventForm,
    /** The form as it stood before the move. */
    val restored: EventForm,
)

sealed interface MoveOutcome {
    /**
     * Written. [startMillis] is where the dragged occurrence now begins, for the
     * confirmation message; [undo] is null when the write has no clean inverse.
     */
    data class Moved(
        val startMillis: Long,
        val isAllDay: Boolean,
        val undo: MoveUndo?,
    ) : MoveOutcome

    data object Undone : MoveOutcome

    /** `WRITE_CALENDAR` was revoked between the drop and the provider call. */
    data object WriteDenied : MoveOutcome

    /** The event vanished (sync, another device) between the read and the write. */
    data object Gone : MoveOutcome

    /** The drop would push the series past its own `UNTIL`, generating nothing. */
    data object BlockedSeriesEnd : MoveOutcome

    data object Failed : MoveOutcome
}

/**
 * Writes a drag-and-drop reschedule (#68) through the same repository calls the
 * edit screen's save uses, so recurring writes, reminder reconciliation and
 * attendee preservation behave identically. Hosted at `CalendarHost` so one
 * instance serves every calendar view and survives view switches.
 *
 * The full prefilled form is carried through the write — never a stripped one —
 * because the occurrence-exception path reconciles reminders and attendees onto
 * the new row, and a partial form would wipe them.
 */
@HiltViewModel
class RescheduleViewModel @Inject constructor(
    private val repository: CalendarRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val _scopePrompt = MutableStateFlow<MoveScopePrompt?>(null)
    val scopePrompt: StateFlow<MoveScopePrompt?> = _scopePrompt.asStateFlow()

    private val _outcome = MutableStateFlow<MoveOutcome?>(null)
    val outcome: StateFlow<MoveOutcome?> = _outcome.asStateFlow()

    private var pending: PreparedMove? = null

    /**
     * The calendars whose events may be dragged. Nothing below the UI guards
     * this — the repository and data source attempt any write handed to them —
     * so a block outside this set registers no drag gesture at all.
     */
    val movableCalendarIds: StateFlow<Set<Long>> = repository.calendars()
        .map { calendars -> calendars.filter { it.allowsEventMove }.map { it.id }.toSet() }
        .catch { emit(emptySet<Long>()) }
        .flowOn(io)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptySet(),
        )

    /** Everything a drop needs, resolved before anything is written. */
    private data class PreparedMove(
        val request: MoveRequest,
        val original: EventForm,
        val updated: EventForm,
        /** True for a series master with a rule — an exception row is not one. */
        val isRecurring: Boolean,
        /**
         * False when the rule names days this move can't re-derive, so anything
         * wider than the single occurrence would leave rule and anchor disagreeing.
         */
        val canRealign: Boolean,
    )

    fun move(request: MoveRequest) {
        if (_scopePrompt.value != null) return
        viewModelScope.launch {
            val prepared = prepare(request) ?: return@launch
            if (!prepared.isRecurring) {
                write(prepared, RecurringWriteScope.AllEvents)
            } else {
                pending = prepared
                _scopePrompt.value = MoveScopePrompt(occurrenceOnly = !prepared.canRealign)
            }
        }
    }

    /** Answer the scope dialog. */
    fun moveWithScope(scope: RecurringWriteScope) {
        val prepared = pending ?: return
        pending = null
        _scopePrompt.value = null
        viewModelScope.launch { write(prepared, scope) }
    }

    /** Dismiss the scope dialog without writing. */
    fun cancelScope() {
        pending = null
        _scopePrompt.value = null
    }

    /** Put a completed move back where it came from. */
    fun undo(undo: MoveUndo) {
        _outcome.value = null
        viewModelScope.launch {
            _outcome.value = try {
                repository.updateEvent(undo.eventId, undo.moved, undo.restored)
                MoveOutcome.Undone
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                MoveOutcome.WriteDenied
            } catch (e: NoSuchEventException) {
                MoveOutcome.Gone
            } catch (e: Exception) {
                MoveOutcome.Failed
            }
        }
    }

    /** Clear the outcome once the screen has shown it. */
    fun consumeOutcome() {
        _outcome.value = null
    }

    private suspend fun prepare(request: MoveRequest): PreparedMove? {
        val detail = try {
            repository.eventDetail(request.eventId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoSuchEventException) {
            _outcome.value = MoveOutcome.Gone
            return null
        } catch (e: Exception) {
            _outcome.value = MoveOutcome.Failed
            return null
        }

        val zone = TimeZone.currentSystemDefault()
        val original = detail.toEditForm(request.beginMillis, request.endMillis, zone)
        val shifted = when (val target = request.target) {
            is MoveTarget.Start -> original.shiftedTo(target.instant, zone)
            is MoveTarget.Day -> original.shiftedByDays(
                (
                    target.date.toEpochDays() -
                        sourceDate(request.beginMillis, original.isAllDay, zone).toEpochDays()
                    ).toInt(),
            )
        }
        // A zero-distance drop is not a write. Matches the edit form's own
        // pristine-form no-op, and keeps a mis-aimed long press harmless.
        if (shifted == original) return null

        val problems = shifted.problems()
        if (EventFormProblem.RecurrenceEndsBeforeStart in problems) {
            _outcome.value = MoveOutcome.BlockedSeriesEnd
            return null
        }
        if (problems.isNotEmpty()) {
            _outcome.value = MoveOutcome.Failed
            return null
        }

        // An exception row stands alone whatever rule a sync adapter left on it.
        val isRecurring = original.rrule != null && !detail.isException
        val movedDay = shifted.start.date != original.start.date
        val realigned = if (isRecurring && movedDay) {
            realignRecurrence(
                requireNotNull(original.rrule),
                original.start.date,
                shifted.start.date,
            )
        } else {
            original.rrule
        }
        return PreparedMove(
            request = request,
            original = original,
            // A rule we can't re-derive stays verbatim: the only scope offered
            // then is the single occurrence, whose exception row carries no rule.
            updated = shifted.copy(rrule = realigned ?: original.rrule),
            isRecurring = isRecurring,
            canRealign = !isRecurring || !movedDay || realigned != null,
        )
    }

    private suspend fun write(prepared: PreparedMove, scope: RecurringWriteScope) {
        val request = prepared.request
        _outcome.value = try {
            when {
                !prepared.isRecurring || scope == RecurringWriteScope.AllEvents ->
                    repository.updateEvent(request.eventId, prepared.original, prepared.updated)

                scope == RecurringWriteScope.ThisEvent ->
                    repository.updateOccurrence(
                        request.eventId,
                        request.beginMillis,
                        prepared.updated,
                    )

                else -> repository.updateEventFromOccurrence(
                    eventId = request.eventId,
                    beginMillis = request.beginMillis,
                    original = prepared.original,
                    updated = prepared.updated,
                )
            }
            MoveOutcome.Moved(
                startMillis = movedStartMillis(prepared),
                isAllDay = prepared.updated.isAllDay,
                undo = undoFor(prepared, scope),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            MoveOutcome.WriteDenied
        } catch (e: NoSuchEventException) {
            MoveOutcome.Gone
        } catch (e: Exception) {
            MoveOutcome.Failed
        }
    }

    /**
     * Undo is offered only where the inverse is one symmetric write: a
     * non-recurring event (absolute DTSTART/DTEND) and a whole-series move (the
     * −Δ wall-clock shift lands on the re-read anchor). "This event" leaves an
     * exception row behind and "this and following" splits the series with an
     * UNTIL truncation — neither is undone by shifting back.
     */
    private fun undoFor(prepared: PreparedMove, scope: RecurringWriteScope): MoveUndo? =
        if (!prepared.isRecurring || scope == RecurringWriteScope.AllEvents) {
            MoveUndo(
                eventId = prepared.request.eventId,
                moved = prepared.updated,
                restored = prepared.original,
            )
        } else {
            null
        }

    /**
     * Where the dragged occurrence now begins, in the same anchoring the views
     * read dates back in: a UTC midnight for an all-day event, the real instant
     * for a timed one.
     */
    private fun movedStartMillis(prepared: PreparedMove): Long {
        val form = prepared.updated
        return if (form.isAllDay) {
            form.start.date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        } else {
            form.start.toInstant(form.resolvedZone(TimeZone.currentSystemDefault()))
                .toEpochMilliseconds()
        }
    }

    /** The calendar day the dragged occurrence started on, as the grid shows it. */
    private fun sourceDate(beginMillis: Long, isAllDay: Boolean, zone: TimeZone): LocalDate =
        Instant.fromEpochMilliseconds(beginMillis)
            .toLocalDateTime(if (isAllDay) TimeZone.UTC else zone)
            .date
}
