package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * How far the finger may wander during the hold. Deliberately well under touch
 * slop (~18dp): past that an ancestor — the vertical scroll, the page swipe —
 * claims the gesture, and a hold that survived to there would have opened a dead
 * zone where neither the drag nor the page turn happens.
 */
private val PICKUP_TOLERANCE = 6.dp

/**
 * Pick an event block up with a long press and drag it, without
 * `detectDragGesturesAfterLongPress`.
 *
 * The stock detector cancels the press as soon as an ancestor consumes — and
 * during the hold the block itself consumes nothing, so the scroll and the page
 * swipe are free to claim at their own slop — and it also cancels when the finger
 * leaves the block, which a `MIN_EVENT_FRACTION`-tall block loses immediately.
 * This one keeps its own timeout and its own (much smaller) tolerance, and never
 * cancels on leaving the bounds.
 *
 * Movement before the timeout is deliberately **not** consumed: consuming it
 * would kill the ancestor's gesture outright, so a scroll that happens to start
 * on top of a block would die. Fast movement means the user meant to scroll —
 * abandon quietly. A second finger means a pinch, which owns the gesture.
 */
@Composable
fun rememberEventDragSource(
    enabled: Boolean,
    key: Any?,
    onPickUp: (pointerInRoot: Offset, blockInRoot: Offset, size: IntSize) -> Unit,
    onMove: (pointerInRoot: Offset) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
): Modifier = rememberDragSurface(
    enabled = enabled,
    key = key,
    onPickUp = { _, pointerInRoot, nodeInRoot, size ->
        onPickUp(pointerInRoot, nodeInRoot, size)
        true
    },
    onMove = onMove,
    onDrop = onDrop,
    onCancel = onCancel,
)

/**
 * The same pickup, for a surface that carries many draggable pieces rather than
 * being one itself — the month grid, whose chips are covered by a full-bleed tap
 * layer and so can never take pointer input of their own. [onPickUp] receives the
 * press position local to this node and answers whether anything is there;
 * returning false abandons the gesture as if the hold had never completed.
 */
@Composable
fun rememberDragSurface(
    enabled: Boolean,
    key: Any?,
    onPickUp: (local: Offset, pointerInRoot: Offset, nodeInRoot: Offset, size: IntSize) -> Boolean,
    onMove: (pointerInRoot: Offset) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
): Modifier {
    if (!enabled) return Modifier
    val holdMillis = LocalViewConfiguration.current.longPressTimeoutMillis
    val tolerance = with(LocalDensity.current) { PICKUP_TOLERANCE.toPx() }
    val coordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val currentPickUp by rememberUpdatedState(onPickUp)
    val currentMove by rememberUpdatedState(onMove)
    val currentDrop by rememberUpdatedState(onDrop)
    val currentCancel by rememberUpdatedState(onCancel)

    return Modifier
        .onGloballyPositioned { coordinates[0] = it }
        .pointerInput(key) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val origin = down.position
                val heldStill = withTimeoutOrNull(holdMillis) {
                    while (true) {
                        val main = awaitPointerEvent()
                        val change = main.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (main.changes.count { it.pressed } > 1) break
                        if ((change.position - origin).getDistance() > tolerance) break
                        // An ancestor's claim only becomes visible once the whole
                        // main pass has run, so look again on the final pass.
                        val final = awaitPointerEvent(PointerEventPass.Final)
                        if (final.changes.any { it.isConsumed }) break
                    }
                } == null
                if (!heldStill) return@awaitEachGesture

                val layout = coordinates[0]?.takeIf { it.isAttached } ?: return@awaitEachGesture
                val took = currentPickUp(
                    down.position,
                    layout.localToRoot(down.position),
                    layout.positionInRoot(),
                    layout.size,
                )
                if (!took) return@awaitEachGesture
                var dropped = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            dropped = true
                            break
                        }
                        // A second finger is the pinch taking over; it consumes on
                        // the initial pass, so the drag is already dead.
                        if (event.changes.count { it.pressed } > 1) break
                        change.consume()
                        coordinates[0]?.takeIf { it.isAttached }
                            ?.let { currentMove(it.localToRoot(change.position)) }
                    }
                } finally {
                    // Also reached when the pointer node is disposed mid-drag (the
                    // page swapping out under the finger) — that is a cancel, not a
                    // drop, and must never write.
                    if (dropped) currentDrop() else currentCancel()
                }
            }
        }
}

/**
 * The app's first haptics: a lift on pickup, then a tick every time the drop
 * target snaps to a different slot, so the granularity is felt rather than read.
 */
@Composable
fun DragSnapHaptics(slot: Any?) {
    val haptics = LocalHapticFeedback.current
    val previous = remember { arrayOfNulls<Any>(1) }
    LaunchedEffect(slot) {
        val had = previous[0]
        previous[0] = slot
        when {
            slot == null -> Unit
            had == null -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            had != slot -> haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }
}
