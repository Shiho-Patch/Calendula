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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.floor
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

    /**
     * Whether fingers are rescaling the timeline right now. Snapshot state, so
     * anything that tweens off [scale] can stand down for the gesture: the
     * height changes every pointer frame, and a spring would spend the whole
     * pinch chasing a target that has already moved.
     */
    var isPinching: Boolean by mutableStateOf(false)
        private set

    /** The visible-hours preference (#322); a timeline re-fits when it changes. */
    var hours: TimelineHours by mutableStateOf(TimelineHours())
        private set

    private var stored: TimelineScale = initial

    /**
     * Take a value that came from the preference. Ignored mid-pinch: the stored
     * value is a frame or two behind the fingers there, and letting it land would
     * snap the timeline back while the user is still pinching. With visible
     * hours on it is only remembered, so a late-arriving preference can't undo
     * the fit a timeline just opened with.
     */
    fun adopt(stored: TimelineScale) {
        this.stored = stored
        if (!isPinching && !hours.enabled) scale = stored
    }

    /** Take the visible-hours preference; switching it off hands back the stored scale. */
    fun adoptHours(hours: TimelineHours) {
        val wasEnabled = this.hours.enabled
        this.hours = hours
        if (wasEnabled && !hours.enabled && !isPinching) scale = stored
    }

    /**
     * A timeline is opening. With visible hours on, the range replaces whatever
     * scale is in force — a stored preset and a pinch alike — and its start hour
     * is returned for the timeline to scroll to; otherwise null.
     */
    fun open(): Int? {
        val hours = hours
        if (!hours.enabled) return null
        if (!isPinching) scale = TimelineScale.FitHours(hours.startHour, hours.endHour)
        return hours.startHour
    }

    fun beginPinch() {
        isPinching = true
    }

    fun pinchTo(hourHeight: Dp) {
        scale = TimelineScale.custom(hourHeight)
    }

    /** Settles the pinch. With visible hours on it only lasts until the next [open]. */
    fun endPinch() {
        isPinching = false
        if (!hours.enabled) persist(scale)
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
 * The app-wide [TimelineZoom], seeded from [stored] and [hours] and writing
 * settled pinches back through [onPersist].
 */
@Composable
fun rememberTimelineZoom(
    stored: TimelineScale,
    hours: TimelineHours,
    onPersist: (TimelineScale) -> Unit,
): TimelineZoom {
    val persist by rememberUpdatedState(onPersist)
    val zoom = remember { TimelineZoom(stored) { persist(it) } }
    LaunchedEffect(hours) { zoom.adoptHours(hours) }
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
            try {
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
                            // Half a pixel, not equality: the height round-trips
                            // through dp and back, and an exact test would read
                            // the float noise that comes back as a scale change
                            // and feed the scroll a delta on every frame of a
                            // held pinch.
                            if (abs(new - old) >= 0.5f) {
                                val centroidY = event.calculateCentroid(useCurrent = true).y
                                pending += anchoredScroll(scrollState.value, centroidY, old, new) -
                                    scrollState.value
                                zoom.pinchTo(new.toDp())
                            }
                            pending -= scrollState.dispatchRawDelta(pending)
                        }
                    }
                    // Hold the gesture to the end once it has become a pinch:
                    // letting go the moment a finger lifts would turn the tail of
                    // a zoom into a scroll, and the taps underneath into a new event.
                    if (claimed) event.changes.forEach { if (it.pressed) it.consume() }
                }
            } finally {
                // In a finally because the gesture can also end by having its
                // pointer node disposed mid-pinch — the timeline swapping out
                // under the fingers. The zoom outlives that node, and a pinch
                // left open would make it ignore every later Settings change.
                if (claimed) zoom.endPinch()
            }
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
 *
 * The ceiling is pulled onto that grid too, downwards, so it stays a height the
 * pinch can actually land on.
 *
 * [fillPx] is deliberately *not* rounded (#290). It is the one height the whole
 * day exactly fills the viewport at, and it is the same value
 * [TimelineScale.FitDay] resolves to — rounding it up by the fraction of a pixel
 * that 24 hours don't divide the viewport into leaves the timeline a pixel per
 * hour taller than its own viewport, so a pinched-all-the-way-out day still
 * scrolls a hair and bounces off Android's overscroll stretch, while the
 * identical FitDay preset sits still. Being the clamp result rather than a bound
 * the gesture is merely held against, it is a height the pinch does land on: the
 * next frame reads it back unchanged and the focal anchor is handed nothing to
 * correct.
 */
internal fun pinchedHourHeightPx(target: Float, fillPx: Float, maxPx: Float): Float =
    // Filling the viewport wins over the ceiling: on a screen tall enough for
    // the two to disagree, dead space is the worse of the two failures.
    target.roundToInt().toFloat()
        .coerceAtMost(floor(maxPx))
        .coerceAtLeast(fillPx)

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

/**
 * Where a timeline opens (#322): scrolled to the start of the visible hours when
 * they are on, otherwise centred on noon. Re-runs when the visible-hours
 * preference changes, so a range picked in Settings shows on the way back.
 */
@Composable
fun OpenTimelineScroll(scrollState: ScrollState, zoom: TimelineZoom) {
    val density = LocalDensity.current
    LaunchedEffect(zoom, zoom.hours) {
        val startHour = zoom.open()
        if (startHour == null) {
            snapshotFlow { scrollState.maxValue }.first { it > 0 }
            // Half the scroll range *is* noon: the content spans a full 24 hours,
            // so centring the range centres midday at whatever hour height is in force.
            scrollState.scrollTo(scrollState.maxValue / 2)
        } else {
            val target = snapshotFlow {
                val viewport = scrollState.viewportSize
                val hourPx = with(density) { zoom.scale.hourHeight(viewport.toDp()).toPx() }
                startHourScroll(scrollState.maxValue, viewport, startHour, hourPx)
            }.filterNotNull().first()
            scrollState.scrollTo(target)
        }
    }
}
