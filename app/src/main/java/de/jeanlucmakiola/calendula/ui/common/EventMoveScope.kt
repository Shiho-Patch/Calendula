package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Drag-to-reschedule wiring (#68), provided once at `CalendarHost` and read by
 * whichever event block is being composed. A composition local rather than six
 * layers of parameters: every calendar surface needs the same three things, and
 * the blocks that need them sit deep inside private composables.
 *
 * Null means moving is off entirely (no host provided it) — a block then
 * registers no drag gesture at all, so a long press keeps its old meaning of
 * nothing happening.
 */
@Immutable
class EventMoveScope(
    /**
     * Calendars whose events may be moved. Nothing below the UI enforces this —
     * the repository writes whatever it is handed — so this gate is load-bearing.
     */
    val movableCalendarIds: Set<Long>,
    val move: (MoveRequest) -> Unit,
    /**
     * True while a dropped event is being written — including the time its scope
     * dialog is up. A flow rather than a value so this scope stays the same
     * object across a move: it is a composition local every visible block reads,
     * and replacing it would recompose all of them twice per drop.
     */
    val inFlight: StateFlow<Boolean>,
    /** Open an event in the edit form — the pointer-free route to the same change. */
    val edit: (EventInstance) -> Unit,
) {
    fun allows(event: EventInstance): Boolean = event.calendarId in movableCalendarIds
}

val LocalEventMove = compositionLocalOf<EventMoveScope?> { null }

private val NEVER_IN_FLIGHT = MutableStateFlow(false)

/**
 * Whether a dropped event is still being written — false wherever moving is off.
 * The drag overlays hold a landed block on its target for this window.
 */
@Composable
fun moveInFlight(): Boolean {
    val flow = LocalEventMove.current?.inFlight ?: NEVER_IN_FLIGHT
    return flow.collectAsStateWithLifecycle().value
}

/** Opacity the source block keeps while its floating copy travels. */
const val GHOST_ALPHA: Float = 0.3f

/**
 * Opacity for a block whose copy is in flight: ghosted while the finger holds
 * it, then faded out once the copy has landed, so the slot it came from is
 * already empty by the time the grid re-reads the move.
 */
@Composable
fun ghostAlpha(lifted: Boolean, landed: Boolean): Float = animateFloatAsState(
    targetValue = when {
        !lifted -> 1f
        landed -> 0f
        else -> GHOST_ALPHA
    },
    label = "ghost-alpha",
).value

/**
 * A TalkBack action that opens [event] in the edit form, so rescheduling isn't
 * pointer-only. Null when this event can't be moved — the block then carries no
 * action and registers no drag either.
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
