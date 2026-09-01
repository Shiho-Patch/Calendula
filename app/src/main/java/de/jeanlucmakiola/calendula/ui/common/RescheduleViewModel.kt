package de.jeanlucmakiola.calendula.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.calendar.NoSuchEventException
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventFormProblem
import de.jeanlucmakiola.calendula.domain.RecurrenceEnd
import de.jeanlucmakiola.calendula.domain.RecurringWriteScope
import de.jeanlucmakiola.calendula.domain.allowsEventMove
import de.jeanlucmakiola.calendula.domain.parseSimpleRecurrence
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import javax.inject.Inject

private const val MILLIS_PER_MINUTE = 60_000
private const val MINUTES_PER_DAY = 24 * 60

/** Where a dragged event should land. */
sealed interface MoveTarget {
    /** A new start instant — a timeline drag, which moves time and day at once. */
    data class Start(val instant: Instant) : MoveTarget

    /**
     * A whole-day shift, keeping the time of day — a month-grid or all-day drag.
     * A delta rather than a target date, so the screen and this view model can't
     * disagree by a day over which zone the event's first day is resolved in.
     */
    data class ByDays(val days: Int) : MoveTarget
}

/**
 * One dropped event. [beginMillis]/[endMillis] are the dragged *occurrence's*
 * own times (`Instances.BEGIN`/`END`), as the detail and edit screens pass them.
 */
data class MoveRequest(
    val eventId: Long,
    val beginMillis: Long,
    val endMillis: Long,
    val target: MoveTarget,
)

/**
 * A recurring drop waiting for the user to pick how far it reaches.
 * [occurrenceOnly] means a wider write would leave the rule and the series
 * anchor disagreeing — either the rule names days one moved occurrence can't
 * re-derive (`BYDAY=MO,WE`, `2TH`, …), or the shift wouldn't carry the anchor
 * across the same midnights the occurrence crossed.
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
 * instance serves every calendar view and survives view switches. The full
 * prefilled form is carried through the write — a stripped one would wipe the
 * reminders and attendees the occurrence-exception path reconciles.
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

    private val _inFlight = MutableStateFlow(false)

    /**
     * True from the moment a drop is accepted until its write settles — the
     * window the dropped block holds its landing position for.
     */
    val inFlight: StateFlow<Boolean> = _inFlight.asStateFlow()

    private val _abandoned = MutableStateFlow(0)

    /**
     * Ticks when a drop ends with nothing on the target day: refused outright,
     * refused before the write, written and failed, or its scope dialog
     * dismissed. [move] answers before any of that is known, so without this
     * signal the view that drew the drop held its copy out for the full settle
     * timeout waiting for a grid that was never going to draw it (#253).
     */
    val abandoned: StateFlow<Int> = _abandoned.asStateFlow()

    private val _undoStarted = MutableStateFlow(0)

    /**
     * Ticks the moment an undo write begins, before the provider has anything to
     * re-read, so the view that drew the drop can carry its chip back rather than
     * let it reappear on the old day.
     */
    val undoStarted: StateFlow<Int> = _undoStarted.asStateFlow()

    /**
     * Set from the moment a drop is accepted until its write settles. Two drops
     * of the same recurring event inside that window would compute their shift
     * from the same pre-move occurrence, and the shifts would compound.
     */
    private var busy = false
        set(value) {
            field = value
            _inFlight.value = value
        }

    /**
     * The calendars whose events may be dragged. Nothing below the UI guards
     * this, so a block outside this set registers no drag gesture at all.
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
        /** False when the rule names days this move can't re-derive. */
        val canRealign: Boolean,
        /** The series row's DTSTART date after the move — what its `UNTIL` must clear. */
        val newAnchorDate: LocalDate,
    )

    /**
     * Take a drop, unless one is already being written. False means nothing will
     * be written; [abandoned] ticks for that refusal too, so a held copy is let
     * go by the one signal rather than by every caller remembering to.
     */
    fun move(request: MoveRequest): Boolean {
        if (busy || _scopePrompt.value != null) {
            _abandoned.value += 1
            return false
        }
        busy = true
        viewModelScope.launch {
            val prepared = prepare(request)
            if (prepared == null) {
                _abandoned.value += 1
                busy = false
                return@launch
            }
            if (!prepared.isRecurring) {
                write(prepared, RecurringWriteScope.AllEvents)
                busy = false
            } else {
                // Still busy: the scope dialog is now the thing in flight.
                pending = prepared
                _scopePrompt.value = MoveScopePrompt(occurrenceOnly = !prepared.canRealign)
            }
        }
        return true
    }

    /** Answer the scope dialog. */
    fun moveWithScope(scope: RecurringWriteScope) {
        val prepared = pending ?: return
        // Guard against the dialog ever offering a scope the rule can't carry.
        if (!prepared.canRealign && scope != RecurringWriteScope.ThisEvent) return
        pending = null
        _scopePrompt.value = null
        viewModelScope.launch {
            write(prepared, scope)
            busy = false
        }
    }

    /** Dismiss the scope dialog without writing. */
    fun cancelScope() {
        pending = null
        busy = false
        _scopePrompt.value = null
        _abandoned.value += 1
    }

    /**
     * Put a completed move back where it came from. The outcome stands until the
     * inverse write reports back, so the one chip changes what it says rather
     * than closing and reopening. False when another write is already running —
     * the chip hides its action for that window, so this is the backstop.
     */
    fun undo(undo: MoveUndo): Boolean {
        if (busy) return false
        busy = true
        _undoStarted.value += 1
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
            busy = false
        }
        return true
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
            is MoveTarget.ByDays -> original.shiftedByDays(target.days, zone)
        }
        // A zero-distance drop is not a write.
        if (shifted == original) return null

        // The UNTIL check is deferred to the write, which knows how far the move
        // reaches and so which date has to clear it.
        if ((shifted.problems() - EventFormProblem.RecurrenceEndsBeforeStart).isNotEmpty()) {
            _outcome.value = MoveOutcome.Failed
            return null
        }

        // An exception row stands alone whatever rule a sync adapter left on it.
        val isRecurring = original.rrule != null && !detail.isException
        val movedDay = shifted.start.date != original.start.date
        // The series anchor moves by the same *wall-clock* shift as the dragged
        // occurrence, so only a whole-day shift carries it across the same number
        // of midnights whatever time of day it sits at. A same-date drag has its
        // own, narrower check — see anchorKeepsItsDay.
        val wholeDayShift = original.isAllDay || shifted.start.time == original.start.time
        val realigned = if (isRecurring && movedDay) {
            if (wholeDayShift) {
                realignRecurrence(
                    requireNotNull(original.rrule),
                    original.start.date,
                    shifted.start.date,
                )
            } else {
                null
            }
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
            canRealign = when {
                !isRecurring -> true
                movedDay -> realigned != null
                else -> anchorKeepsItsDay(detail, original, shifted, zone)
            },
            // Where the series row's own DTSTART lands, given the anchor moves by
            // the same shift. Only read under canRealign, which is what
            // guarantees the anchor really does travel this many days.
            newAnchorDate = anchorDate(detail, original, zone)
                .plus(shifted.start.date.toEpochDays() - original.start.date.toEpochDays(), DateTimeUnit.DAY),
        )
    }

    private suspend fun write(prepared: PreparedMove, scope: RecurringWriteScope) {
        val request = prepared.request
        if (endsBeforeItStarts(prepared, scope)) {
            _outcome.value = MoveOutcome.BlockedSeriesEnd
            _abandoned.value += 1
            return
        }
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
        // Nothing landed, so the copy the view is holding has nothing to fade
        // into — let it go now rather than at the end of the settle timeout.
        if (_outcome.value !is MoveOutcome.Moved) _abandoned.value += 1
    }

    /**
     * Whether this write would leave a rule whose `UNTIL` precedes the first day
     * it now applies to — the provider then generates nothing at all. Which date
     * has to clear `UNTIL` depends on the scope: a whole-series move carries the
     * series *anchor*, a split starts at the moved occurrence, and a single
     * occurrence becomes an exception row that no `UNTIL` constrains.
     */
    private fun endsBeforeItStarts(prepared: PreparedMove, scope: RecurringWriteScope): Boolean {
        if (!prepared.isRecurring || scope == RecurringWriteScope.ThisEvent) return false
        val rule = prepared.updated.rrule ?: return false
        val end = parseSimpleRecurrence(rule)?.end as? RecurrenceEnd.Until ?: return false
        val firstDay = if (scope == RecurringWriteScope.AllEvents) {
            prepared.newAnchorDate
        } else {
            prepared.updated.start.date
        }
        return end.date < firstDay
    }

    /**
     * Whether a same-date drag leaves the series anchor on its own day too. The
     * anchor moves by the same wall-clock delta as the occurrence, and normally
     * shares its time of day — but a row with no `EVENT_TIMEZONE` resolves the
     * two in zones that can sit a DST hour apart, so a near-midnight drag could
     * carry the anchor across a midnight the occurrence never crossed and leave
     * `BYDAY` naming the wrong weekday.
     */
    private fun anchorKeepsItsDay(
        detail: EventDetail,
        original: EventForm,
        shifted: EventForm,
        zone: TimeZone,
    ): Boolean {
        val anchorZone = if (original.isAllDay) TimeZone.UTC else original.resolvedZone(zone)
        val anchorMinute = detail.instance.start.toLocalDateTime(anchorZone)
            .time.toMillisecondOfDay() / MILLIS_PER_MINUTE
        val delta = (
            shifted.start.time.toMillisecondOfDay() - original.start.time.toMillisecondOfDay()
            ) / MILLIS_PER_MINUTE
        return anchorMinute + delta in 0 until MINUTES_PER_DAY
    }

    /**
     * The series row's own start date — for a recurring master, `EventDetail`
     * carries the row's DTSTART rather than the tapped occurrence's. Read in the
     * same anchoring the write path uses: UTC for an all-day series.
     */
    private fun anchorDate(detail: EventDetail, original: EventForm, zone: TimeZone): LocalDate =
        detail.instance.start
            .toLocalDateTime(if (original.isAllDay) TimeZone.UTC else original.resolvedZone(zone))
            .date

    /**
     * Undo is offered only where the inverse is one symmetric write: a
     * non-recurring event, and a whole-series move. "This event" leaves an
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
     * read dates back in: UTC midnight for an all-day event.
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

}
