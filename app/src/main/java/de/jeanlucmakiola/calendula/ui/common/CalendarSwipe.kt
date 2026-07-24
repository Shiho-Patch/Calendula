package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Drag distance that turns a calendar page, shared by the month, week and day
 * views so all three answer a swipe at the same point.
 *
 * It was 6dp once, which is inside the distance a tap wanders: brushing the grid
 * changed the month, and a page that turns on an unintended gesture reads as the
 * animation misfiring rather than as the gesture being over-eager.
 */
val CALENDAR_SWIPE_THRESHOLD = 24.dp

/**
 * The whole-page horizontal swipe: one page per gesture, committed **the moment
 * the drag clears [CALENDAR_SWIPE_THRESHOLD]** rather than when the finger lifts.
 *
 * Waiting for the lift meant the page sat still under a finger that had already
 * travelled far enough to ask for it, and the answer only arrived once you let
 * go — which reads as the view being slow rather than as a deliberate commit.
 * Firing on the threshold is what makes the gesture feel like it is being
 * followed. The trade is that a drag can no longer be taken back by dragging the
 * other way; in practice, once you have moved 24dp deliberately you meant it, and
 * the page you land on is one swipe back.
 *
 * Deliberately **horizontal-only**. The week and day timelines scroll vertically
 * underneath this, and a two-dimensional detector here would claim those drags
 * before the inner scroll ever saw them. As it is, a horizontal drag crosses this
 * detector's slop while a vertical one is consumed below, and the two coexist.
 * (The month view's split style needs a vertical axis as well, so it keeps its
 * own axis-locking detector rather than using this.)
 */
@Composable
fun rememberCalendarPageSwipe(
    onSwipeNext: () -> Unit,
    onSwipePrev: () -> Unit,
): Modifier {
    val threshold = with(LocalDensity.current) { CALENDAR_SWIPE_THRESHOLD.toPx() }
    return Modifier.pointerInput(onSwipeNext, onSwipePrev) {
        var accum = 0f
        // One page per gesture: without this a long drag would keep re-firing
        // every time the accumulator crossed the threshold again.
        var fired = false
        detectHorizontalDragGestures(
            onDragStart = {
                accum = 0f
                fired = false
            },
            onDragEnd = {
                accum = 0f
                fired = false
            },
            onDragCancel = {
                accum = 0f
                fired = false
            },
            onHorizontalDrag = { _, drag ->
                accum += drag
                if (!fired) {
                    val commit = when {
                        accum < -threshold -> onSwipeNext
                        accum > threshold -> onSwipePrev
                        else -> null
                    }
                    if (commit != null) {
                        fired = true
                        commit()
                    }
                }
            },
        )
    }
}
