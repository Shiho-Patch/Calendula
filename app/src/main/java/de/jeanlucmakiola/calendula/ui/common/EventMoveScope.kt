package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance

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
    /** Open an event in the edit form — the pointer-free route to the same change. */
    val edit: (EventInstance) -> Unit,
) {
    fun allows(event: EventInstance): Boolean = event.calendarId in movableCalendarIds
}

val LocalEventMove = compositionLocalOf<EventMoveScope?> { null }

/** Opacity the source block keeps while its floating copy travels. */
const val GHOST_ALPHA: Float = 0.3f

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
