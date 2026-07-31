package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The scale the timelines draw at right now: the stored preference, or whatever
 * a pinch is currently doing to it (#56).
 *
 * A pinch changes the scale on every pointer frame, which the preference alone
 * cannot carry — a DataStore round trip per frame would lag the gesture badly
 * behind the fingers. So the live value lives here in composition state and only
 * the settled result is written back, once, when the fingers lift.
 */
@Stable
class TimelineZoom(
    initial: TimelineScale,
    private val persist: (TimelineScale) -> Unit,
) {
    /** What the week and day timelines should draw at. */
    var scale: TimelineScale by mutableStateOf(initial)
        private set

    private var pinching = false

    /**
     * Take a value that came from the preference. Ignored mid-pinch: the stored
     * value is a frame or two behind the fingers there, and letting it land would
     * snap the timeline back while the user is still pinching.
     */
    fun adopt(stored: TimelineScale) {
        if (!pinching) scale = stored
    }

    fun beginPinch() {
        pinching = true
    }

    fun pinchTo(hourHeight: Dp) {
        scale = TimelineScale.custom(hourHeight)
    }

    fun endPinch() {
        pinching = false
        persist(scale)
    }
}

/**
 * The zoom the timelines read. Falls back to a detached instance — one shared
 * lazy value, not one per read, or each read would hand out a fresh state — so a
 * preview or test can render a timeline without the activity's provider.
 */
private val DetachedTimelineZoom by lazy { TimelineZoom(TimelineScale.Regular) {} }

val LocalTimelineZoom = staticCompositionLocalOf { DetachedTimelineZoom }

/**
 * The app-wide [TimelineZoom], seeded from [stored] and writing settled pinches
 * back through [onPersist].
 */
@Composable
fun rememberTimelineZoom(
    stored: TimelineScale,
    onPersist: (TimelineScale) -> Unit,
): TimelineZoom {
    val persist by rememberUpdatedState(onPersist)
    val zoom = remember { TimelineZoom(stored) { persist(it) } }
    // Picking a preset in Settings has to reach the timelines, and so does the
    // stored value arriving after the first frame.
    LaunchedEffect(stored) { zoom.adopt(stored) }
    return zoom
}

/**
 * Two-finger pinch that rescales a 24-hour timeline (#56) — the gesture the
 * issue actually asked for; the Settings presets stay as the accessible route to
 * the same thing.
 *
 * Three gestures share this area, so the pinch is deliberately the fussiest
 * about claiming it: nothing happens until a *second* finger is down and the
 * spread has grown past [PINCH_SLOP], which leaves one-finger vertical scrolling
 * and the horizontal week swipe untouched, and lets a two-finger drag still
 * scroll. It watches on [PointerEventPass.Initial] because the scroll it has to
 * outrank is a descendant — on the main pass the scroll would have consumed the
 * drag before this ever saw it.
 *
 * [hourHeight] is the *resolved* height, so a pinch that starts from
 * `FitDay` picks up where the viewport left it rather than jumping.
 * [viewportHeight] sets how far out it can go — see [fillHourHeight].
 */
@Composable
fun rememberTimelinePinchZoom(
    scrollState: ScrollState,
    viewportHeight: Dp,
    hourHeight: Dp,
    zoom: TimelineZoom,
): Modifier {
    // The gesture loop outlives any single composition, so it reads these
    // through state handles rather than capturing what they were when it started.
    val currentHourHeight = rememberUpdatedState(hourHeight)
    val currentViewport = rememberUpdatedState(viewportHeight)
    return Modifier.pointerInput(scrollState, zoom) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var claimed = false
            var slop = 1f
            // Scroll the layout could not give us yet, carried to the next frame
            // (see anchoredScroll).
            var pending = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.none { it.pressed }) break
                if (event.changes.count { it.pressed } >= 2) {
                    val step = event.calculateZoom()
                    if (!claimed) {
                        slop *= step
                        if (abs(slop - 1f) >= PINCH_SLOP) {
                            claimed = true
                            zoom.beginPinch()
                        }
                    }
                    if (claimed) {
                        val old = currentHourHeight.value.toPx()
                        val new = pinchedHourHeightPx(
                            target = old * step,
                            fillPx = fillHourHeight(currentViewport.value).toPx(),
                            maxPx = MAX_PINCH_HOUR_HEIGHT.toPx(),
                        )
                        if (new != old) {
                            val centroidY = event.calculateCentroid(useCurrent = true).y
                            pending += anchoredScroll(scrollState.value, centroidY, old, new) -
                                scrollState.value
                            zoom.pinchTo(new.toDp())
                        }
                        pending -= scrollState.dispatchRawDelta(pending)
                    }
                }
                // Hold the gesture to the end once it has become a pinch: letting
                // go the moment a finger lifts would turn the tail of a zoom into
                // a scroll, and the taps underneath into a new event.
                if (claimed) event.changes.forEach { if (it.pressed) it.consume() }
            }
            if (claimed) zoom.endPinch()
        }
    }
}

/**
 * Where a pinch aiming at [target] px per hour actually lands: clamped between
 * "the whole day fills the screen" ([fillPx]) and [maxPx], then rounded to a
 * whole pixel.
 *
 * The rounding is not cosmetic. The hour gutter is 24 stacked boxes one hour
 * tall, and each rounds its own height to whole pixels, while the hour lines and
 * event blocks are drawn at the fractional height — so a height of 56.4px lays
 * the labels out at 56px and leaves the 23:00 label ~9px above the line it
 * names, jumping the whole column as a pinch drifts across each half pixel.
 * Pinning the hour to whole pixels keeps every part of the timeline on one grid.
 */
internal fun pinchedHourHeightPx(target: Float, fillPx: Float, maxPx: Float): Float =
    // Filling the viewport wins over the ceiling: on a screen tall enough for
    // the two to disagree, dead space is the worse of the two failures.
    target.coerceAtMost(maxPx).coerceAtLeast(fillPx).roundToInt().toFloat()

/**
 * The scroll offset that keeps the moment under [centroidY] under it after the
 * hour height changes from [oldHourPx] to [newHourPx].
 *
 * Anchoring on the fingers is what makes a zoom feel like the day is being
 * stretched rather than replaced: without it, zooming in on the evening walks
 * the evening off the bottom of the screen.
 *
 * The caller may not get all of this at once — the content only grows on the
 * next layout pass, so scrolling further down than the *current* content allows
 * is refused — which is why the shortfall is carried over and re-offered.
 */
internal fun anchoredScroll(
    scroll: Int,
    centroidY: Float,
    oldHourPx: Float,
    newHourPx: Float,
): Float = ((scroll + centroidY) / oldHourPx) * newHourPx - centroidY

/**
 * How far the fingers must spread or close before the pinch takes over. Small
 * enough to feel immediate, wide enough that the two-finger scroll a user meant
 * as a scroll stays one.
 */
private const val PINCH_SLOP = 0.08f
