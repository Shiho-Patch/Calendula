package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.week.MINUTES_PER_DAY
import de.jeanlucmakiola.calendula.ui.week.TimedBlock
import de.jeanlucmakiola.floret.identity.rememberReduceMotion
import de.jeanlucmakiola.floret.locale.currentLocale
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Instant

/** Start granularity a dragged block snaps to, matching the big calendar apps. */
const val DRAG_SNAP_MINUTES: Int = 15

/** How close to a timeline edge the finger must get before the view scrolls. */
private val AUTO_SCROLL_EDGE = 64.dp

/** Fastest auto-scroll step, per frame, right at the edge. */
private val AUTO_SCROLL_STEP = 16.dp

/** A block being dragged, in root coordinates so it can be drawn in an overlay. */
data class TimelineDrag(
    val event: EventInstance,
    val date: LocalDate,
    val startMin: Int,
    val endMin: Int,
    val topLeftInRoot: Offset,
    val sizePx: IntSize,
) {
    /** What the snap haptics key off: one tick per changed slot, not per frame. */
    val slot: Pair<LocalDate, Int> get() = date to startMin
}

/** Where a finished drag asks its event to go. */
data class TimelineDrop(val event: EventInstance, val date: LocalDate, val startMin: Int)

/**
 * The timeline's live geometry, republished on every layout. Held in plain
 * fields rather than snapshot state on purpose: it changes on every scroll
 * frame, and recomposing the screen that often would cost far more than the
 * drag loop's own per-frame read of it.
 */
class TimelineGeometry {
    /** The day-columns row — scrolling *content*, so its root position folds in the scroll. */
    var grid: LayoutCoordinates? = null

    /** The scroll viewport, for the edge that triggers auto-scrolling. */
    var viewport: LayoutCoordinates? = null
    var scroll: ScrollState? = null
    var hourPx: Float = 0f

    /** Column pitch — one column plus the gap after it. */
    var columnWidthPx: Float = 0f
    var columnGapPx: Float = 0f
    var edgePx: Float = 0f
    var stepPx: Float = 0f
    var days: List<LocalDate> = emptyList()

    /**
     * Whether the columns are laid out right-to-left. Pointer coordinates are
     * never mirrored, but the grid is, so the leftmost column is the *last* day
     * in Arabic — the mapping has to flip with it.
     */
    var isRtl: Boolean = false
}

/**
 * Hoisted drag state for one timeline (#68). It lives above the per-page
 * `AnimatedContent` — a page change mid-drag would otherwise strand a ghost —
 * and the block it renders is drawn in an overlay: a `Card` column ends its
 * modifier chain with a clip, so a block offset toward the neighbouring column
 * would simply be cut off in place.
 */
@Stable
class TimelineDragController {
    val geometry = TimelineGeometry()

    var drag: TimelineDrag? by mutableStateOf(null)
        private set

    /**
     * A dropped block, held at the slot it landed on while the write runs. The
     * grid behind it still shows the old time until the provider notifies and
     * the query re-reads, so releasing the copy at drop time would snap the
     * event back to where it came from for the length of the write.
     */
    var settling: TimelineDrag? by mutableStateOf(null)
        private set

    /**
     * Which block is lifted, and whether anything is. Separate snapshot state
     * from [drag] on purpose: [drag] changes on every frame, and the blocks that
     * only need to know "am I the ghost" must not recompose that often. Stays
     * set through [settling], so the source never reappears under the copy.
     */
    var liftedInstanceId: Long? by mutableStateOf(null)
        private set

    /**
     * Where the settled drop came from, and which event row it belongs to. The
     * instance id alone can't identify the source block for the length of the
     * write: the provider regenerates `Instances` rows, so a re-read that still
     * carries the *old* time can arrive under a new instance id — and a source
     * matched by instance id would stop being the ghost and flash back to full
     * opacity in the slot the event is about to leave.
     */
    private var settledOrigin: Triple<Long, LocalDate, Int>? by mutableStateOf(null)

    /** Whether a finger is on a block right now — [settling] is not dragging. */
    var isDragging: Boolean by mutableStateOf(false)
        private set

    /**
     * Whether the grid itself now draws the settled drop at its landing slot.
     * The copy may only be handed back once this is true: releasing on a timer
     * puts the source ghost back at full opacity in its *old* slot for whatever
     * is left of the re-read — a flicker of the event where it no longer is.
     */
    var settledOnGrid: Boolean by mutableStateOf(false)
        private set

    private var source: TimedBlock? = null
    private var grab = Offset.Zero
    private var pointer = Offset.Zero

    /**
     * The slot the block already occupied when it was picked up. A long press
     * that never moves must write nothing — and because the target snaps to the
     * grid, "nothing moved" is not the same as "the start is unchanged": an
     * event at 09:07 resolves to 09:00 the instant it lifts.
     */
    private var originSlot: Pair<LocalDate, Int>? = null

    fun begin(block: TimedBlock, pointerInRoot: Offset, blockInRoot: Offset) {
        source = block
        settling = null
        isDragging = true
        liftedInstanceId = block.event.instanceId
        grab = pointerInRoot - blockInRoot
        pointer = pointerInRoot
        originSlot = null
        recompute()
        originSlot = drag?.slot
    }

    fun move(pointerInRoot: Offset) {
        pointer = pointerInRoot
        recompute()
    }

    fun cancel() {
        source = null
        isDragging = false
        liftedInstanceId = null
        originSlot = null
        drag = null
        settling = null
        settledOnGrid = false
        settledOrigin = null
    }

    /**
     * Whether [block] is the one whose copy is in flight, and so must stay a
     * ghost. While the finger holds it that is the instance it picked up; once
     * dropped it is also whatever now sits in the slot it left, however the
     * provider has renumbered it in the meantime.
     */
    fun ghosts(block: TimedBlock, date: LocalDate): Boolean {
        if (liftedInstanceId == null) return false
        if (block.event.instanceId == liftedInstanceId) return true
        val (eventId, originDate, originMin) = settledOrigin ?: return false
        return block.event.eventId == eventId &&
            date == originDate &&
            block.startMin == originMin
    }

    /**
     * What a day column now holds, so a settled drop can tell when the grid has
     * caught up with it. Matched on the landing slot plus either the event row
     * or its title: a single-occurrence move writes an exception row with a new
     * `eventId`, which nothing else about the drop can predict.
     */
    fun noteGrid(date: LocalDate, blocks: List<TimedBlock>) {
        val landed = settling ?: return
        if (settledOnGrid || landed.date != date) return
        settledOnGrid = blocks.any { block ->
            block.startMin == landed.startMin &&
                (
                    block.event.eventId == landed.event.eventId ||
                        block.event.title == landed.event.title
                    )
        }
    }

    /**
     * End the drag, handing back where it landed — null when it never resolved,
     * or when it landed back on the slot it started from. A real drop keeps its
     * copy on the target as [settling] until [release].
     */
    fun finish(): TimelineDrop? {
        val landed = drag
        val origin = originSlot
        cancel()
        if (landed == null || landed.slot == origin) return null
        settling = landed
        liftedInstanceId = landed.event.instanceId
        settledOrigin = origin?.let { (date, min) -> Triple(landed.event.eventId, date, min) }
        return TimelineDrop(landed.event, landed.date, landed.startMin)
    }

    /**
     * Stop ghosting the source — the grid draws the drop itself by now — while
     * the copy is still on screen dissolving. Un-ghosting only at [release]
     * would leave the block under the fading copy dim, and brighten it once the
     * copy was gone: a dip the eye reads as the event flickering.
     */
    fun handOver() {
        liftedInstanceId = null
        settledOrigin = null
    }

    /** Drop the copy, once it has faded into the grid's own block. */
    fun release() {
        settling = null
        settledOnGrid = false
        handOver()
    }

    /**
     * Re-derive the target from the pointer's *root* position. Recomputed rather
     * than accumulated from `positionChange()`, because a stationary finger emits
     * no move events while auto-scroll walks the content underneath it.
     */
    fun recompute() {
        val block = source ?: return
        val grid = geometry.grid?.takeIf { it.isAttached } ?: return
        val hourPx = geometry.hourPx
        val columnPx = geometry.columnWidthPx
        val days = geometry.days
        if (hourPx <= 0f || columnPx <= 0f || days.isEmpty()) return

        val origin = grid.positionInRoot()
        val rawMinutes = (pointer.y - grab.y - origin.y) / hourPx * 60f
        val snapped = (rawMinutes / DRAG_SNAP_MINUTES).roundToInt() * DRAG_SNAP_MINUTES
        // Clamp the start into the target day; a tail running past midnight is
        // fine and stays visible on the next day.
        val startMin = snapped.coerceIn(0, MINUTES_PER_DAY - DRAG_SNAP_MINUTES)
        val span = block.endMin - block.startMin
        // The column the finger is over on screen, and the day that column shows.
        val column = ((pointer.x - origin.x) / columnPx).toInt().coerceIn(0, days.lastIndex)
        val dayIndex = if (geometry.isRtl) days.lastIndex - column else column
        val height = maxOf(span / 60f * hourPx, MIN_EVENT_FRACTION * hourPx)
        drag = TimelineDrag(
            event = block.event,
            date = days[dayIndex],
            startMin = startMin,
            endMin = startMin + span,
            topLeftInRoot = Offset(
                x = origin.x + column * columnPx,
                y = origin.y + startMin / 60f * hourPx,
            ),
            sizePx = IntSize(
                (columnPx - geometry.columnGapPx).roundToInt(),
                height.roundToInt(),
            ),
        )
    }

    /**
     * Hold the scroll for the whole drag and nudge it once per frame while the
     * finger sits near an edge. One `scroll` call, not a loop of
     * `animateScrollBy`: each of those re-acquires the mutex and cancels the last.
     */
    suspend fun autoScroll() {
        val scroll = geometry.scroll ?: return
        scroll.scroll(MutatePriority.UserInput) {
            while (true) {
                withFrameNanos { }
                recompute()
                val step = edgeStep()
                if (step != 0f) scrollBy(step)
            }
        }
    }

    private fun edgeStep(): Float {
        val viewport = geometry.viewport?.takeIf { it.isAttached } ?: return 0f
        val edge = geometry.edgePx
        if (edge <= 0f) return 0f
        val bounds = viewport.boundsInRoot()
        val fromTop = pointer.y - bounds.top
        val fromBottom = bounds.bottom - pointer.y
        return when {
            fromTop < edge -> -(edge - fromTop) / edge * geometry.stepPx
            fromBottom < edge -> (edge - fromBottom) / edge * geometry.stepPx
            else -> 0f
        }
    }
}

@Composable
fun rememberTimelineDragController(): TimelineDragController {
    val controller = remember { TimelineDragController() }
    val density = LocalDensity.current
    controller.geometry.edgePx = with(density) { AUTO_SCROLL_EDGE.toPx() }
    controller.geometry.stepPx = with(density) { AUTO_SCROLL_STEP.toPx() }
    return controller
}

/**
 * True when [block] actually begins on [day] rather than being the tail of an
 * event that started earlier. A clipped block's top edge is midnight, not the
 * event's start, so dragging it would move the event to a time it never had.
 */
fun TimedBlock.beginsOn(day: LocalDate, zone: TimeZone): Boolean =
    event.start.toLocalDateTime(zone).date == day

/**
 * The instant a drop asks for, read in the zone the timeline is drawn in. A drop
 * into a spring-forward gap has no instant of its own; `toInstant` resolves it
 * forward by the missing hour, and the block visibly settles there.
 */
fun TimelineDrop.startInstant(zone: TimeZone): Instant =
    LocalDateTime(date, LocalTime(startMin / 60, startMin % 60)).toInstant(zone)

/**
 * A beat after the grid draws the drop, so its own block is under way before the
 * copy starts dissolving.
 */
const val SETTLE_GRACE_MILLIS: Long = 60L

/**
 * How long to wait for a grid that never confirms the drop — the event landed on
 * a day this timeline doesn't show, or the write failed and nothing changed. The
 * copy has to go either way.
 */
private const val SETTLE_TIMEOUT_MILLIS = 900L

/**
 * The hand-over: the copy dissolves over this while the grid's own block slides
 * in under it, so the two overlap rather than one replacing the other. Kept
 * short, because a full-width copy sits over any neighbour it now shares a lane
 * with until it is gone.
 */
const val SETTLE_FADE_MILLIS: Int = 250

/**
 * The floating block, drawn over the whole calendar so it is free of the day
 * column's clip and of the scroll viewport's rounded corners.
 */
@Composable
fun TimelineDragOverlay(controller: TimelineDragController, modifier: Modifier = Modifier) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val dark = isSystemInDarkTheme()
    val soften = LocalSoftenColors.current
    val use24Hour = LocalUse24HourFormat.current
    val locale = currentLocale()
    val reduceMotion = rememberReduceMotion()
    val density = LocalDensity.current
    val moveInFlight = moveInFlight()

    // Both live here rather than beside the controller: they read [drag], which
    // changes every frame, and this composable is the one that is meant to.
    DragSnapHaptics(controller.drag?.slot)
    LaunchedEffect(controller.isDragging) {
        if (controller.isDragging) controller.autoScroll()
    }
    // Hold the landed copy until the write is done — including the whole time a
    // recurring drop's scope dialog is up — and then until the grid draws the
    // drop itself, so the copy dissolves onto a block that is already there.
    var handingOver by remember(controller.settling) { mutableStateOf(false) }
    LaunchedEffect(controller.settling, moveInFlight, controller.settledOnGrid) {
        if (controller.settling == null || moveInFlight) return@LaunchedEffect
        delay(if (controller.settledOnGrid) SETTLE_GRACE_MILLIS else SETTLE_TIMEOUT_MILLIS)
        handingOver = true
        controller.handOver()
        delay(SETTLE_FADE_MILLIS.toLong())
        controller.release()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // A copy of a block that is still in the tree behind it; announcing
            // it again would just duplicate the event for the drag's duration.
            .clearAndSetSemantics { }
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        val drag = controller.drag ?: controller.settling ?: return@Box
        // Landed: the copy sinks back to the grid's own plane while the write
        // runs, so the release reads as the block settling rather than vanishing.
        val landed = controller.drag == null
        val lift by animateFloatAsState(
            targetValue = if (landed || reduceMotion) 0f else 1f,
            label = "drag-lift",
        )
        val copyAlpha by animateFloatAsState(
            targetValue = if (handingOver) 0f else 1f,
            animationSpec = tween(SETTLE_FADE_MILLIS),
            label = "drag-handover",
        )
        val fill = eventFill(drag.event.color, dark, soften)
        val title = drag.event.title.ifBlank { stringResource(R.string.event_untitled) }
        val label = "${formatMinuteOfDay(drag.startMin, use24Hour, locale)}–" +
            formatMinuteOfDay(drag.endMin.coerceAtMost(MINUTES_PER_DAY), use24Hour, locale)
        Box(
            modifier = Modifier
                // Absolute: these are root coordinates, and the direction-aware
                // offset would mirror them across the screen in an RTL layout.
                .absoluteOffset {
                    IntOffset(
                        (drag.topLeftInRoot.x - origin.x).roundToInt(),
                        (drag.topLeftInRoot.y - origin.y).roundToInt(),
                    )
                }
                .size(
                    width = with(density) { drag.sizePx.width.toDp() },
                    height = with(density) { drag.sizePx.height.toDp() },
                )
                .padding(horizontal = 1.dp)
                .graphicsLayer {
                    scaleX = 1f + 0.02f * lift
                    scaleY = 1f + 0.02f * lift
                    shadowElevation = 8.dp.toPx() * lift
                    alpha = copyAlpha
                    shape = RoundedCornerShape(4.dp)
                    clip = false
                }
                .background(fill, RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = eventInk(fill, alpha = 0.85f),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = eventInk(fill, alpha = SECONDARY_INK_ALPHA),
                )
            }
        }
    }
}
