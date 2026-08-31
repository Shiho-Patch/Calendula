package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Drag-to-reschedule wiring (#68), provided once at `CalendarHost` and read by
 * whichever event block is being composed. Null means moving is off entirely and
 * blocks register no drag gesture at all.
 */
@Immutable
class EventMoveScope(
    /**
     * Calendars whose events may be moved. Nothing below the UI enforces this —
     * the repository writes whatever it is handed — so this gate is load-bearing.
     */
    val movableCalendarIds: Set<Long>,
    /**
     * Whether the *gesture* is on (#173). Off gates the drag alone: [edit] and the
     * TalkBack action below stay, since invoking a menu item can't cause the
     * accidental drags the setting exists to stop.
     */
    val dragEnabled: Boolean,
    /** False when the drop was refused outright, so nothing will be written. */
    val move: (MoveRequest) -> Boolean,
    /**
     * True while a dropped event is being written, including the time its scope
     * dialog is up. A flow rather than a value so this scope stays the same
     * object across a move — every visible block reads it as a composition local.
     */
    val inFlight: StateFlow<Boolean>,
    /** Ticks when an undo write begins — see `RescheduleViewModel.undoStarted`. */
    val undoStarted: StateFlow<Int>,
    /**
     * Ticks when a drop ends with nothing landing — see
     * `RescheduleViewModel.abandoned`. [move] answers before the write is
     * resolved, so this is what tells a held copy to stop waiting.
     */
    val abandoned: StateFlow<Int>,
    /** Open an event in the edit form — the pointer-free route to the same change. */
    val edit: (EventInstance) -> Unit,
) {
    fun allows(event: EventInstance): Boolean = event.calendarId in movableCalendarIds
}

val LocalEventMove = compositionLocalOf<EventMoveScope?> { null }

private val NEVER_IN_FLIGHT = MutableStateFlow(false)

private val NEVER_UNDONE = MutableStateFlow(0)

private val NEVER_ABANDONED = MutableStateFlow(0)

/**
 * Whether a dropped event is still being written — false wherever moving is off.
 * The drag overlays hold a landed block on its target for this window.
 */
@Composable
fun moveInFlight(): Boolean {
    val flow = LocalEventMove.current?.inFlight ?: NEVER_IN_FLIGHT
    return flow.collectAsStateWithLifecycle().value
}

/**
 * How many drops have ended with nothing landing. Read as a plain count so a
 * drag overlay can capture it at the moment of the drop and tell, later, whether
 * *its own* drop was one of them.
 */
@Composable
fun abandonedMoves(): Int {
    val flow = LocalEventMove.current?.abandoned ?: NEVER_ABANDONED
    return flow.collectAsStateWithLifecycle().value
}

/**
 * Runs [onUndo] when an undo write begins, and never for one that began before
 * this composable came on screen.
 */
@Composable
fun OnUndoStarted(onUndo: () -> Unit) {
    val flow = LocalEventMove.current?.undoStarted ?: NEVER_UNDONE
    val tick by flow.collectAsStateWithLifecycle()
    var seen by remember { mutableIntStateOf(tick) }
    LaunchedEffect(tick) {
        if (tick == seen) return@LaunchedEffect
        seen = tick
        onUndo()
    }
}

/** Opacity the source block keeps while its floating copy travels. */
const val GHOST_ALPHA: Float = 0.3f

/**
 * Opacity for a block whose copy is in flight: ghosted from the lift until the
 * copy is handed back, then animated up rather than switched, so the block
 * appears to travel to its new slot instead of vanishing and reappearing.
 */
@Composable
fun ghostAlpha(lifted: Boolean): Float = animateFloatAsState(
    targetValue = if (lifted) GHOST_ALPHA else 1f,
    label = "ghost-alpha",
).value

/**
 * Whether [event] can be picked up and dragged — its calendar allows moving and
 * the gesture is switched on. Not the same question as [eventMoveAction], which
 * survives the setting.
 */
@Composable
fun eventDragAllowed(event: EventInstance): Boolean {
    val move = LocalEventMove.current ?: return false
    return move.dragEnabled && move.allows(event)
}

/**
 * A TalkBack action that opens [event] in the edit form, so rescheduling isn't
 * pointer-only. Null when this event can't be moved.
 */
@Composable
fun eventMoveAction(event: EventInstance): CustomAccessibilityAction? {
    val move = LocalEventMove.current ?: return null
    if (!move.allows(event)) return null
    val label = stringResource(R.string.event_move_action)
    return remember(event.instanceId, label, move) {
        CustomAccessibilityAction(label) {
            move.edit(event)
            true
        }
    }
}
