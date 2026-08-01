package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
     * Which block is lifted, and whether anything is. Separate snapshot state
     * from [drag] on purpose: [drag] changes on every frame, and the blocks that
     * only need to know "am I the ghost" must not recompose that often.
     */
    var liftedInstanceId: Long? by mutableStateOf(null)
        private set

    private var source: TimedBlock? = null
    private var grab = Offset.Zero
    private var pointer = Offset.Zero

    val isDragging: Boolean get() = liftedInstanceId != null

    fun begin(block: TimedBlock, pointerInRoot: Offset, blockInRoot: Offset) {
        source = block
        liftedInstanceId = block.event.instanceId
        grab = pointerInRoot - blockInRoot
        pointer = pointerInRoot
        recompute()
    }

    fun move(pointerInRoot: Offset) {
        pointer = pointerInRoot
        recompute()
    }

    fun cancel() {
        source = null
        liftedInstanceId = null
        drag = null
    }

    /** End the drag, handing back where it landed (null if it never resolved). */
    fun finish(): TimelineDrop? {
        val landed = drag
        cancel()
        return landed?.let { TimelineDrop(it.event, it.date, it.startMin) }
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
        val column = ((pointer.x - origin.x) / columnPx).toInt().coerceIn(0, days.lastIndex)
        val height = maxOf(span / 60f * hourPx, MIN_EVENT_FRACTION * hourPx)
        drag = TimelineDrag(
            event = block.event,
            date = days[column],
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

    // Both live here rather than beside the controller: they read [drag], which
    // changes every frame, and this composable is the one that is meant to.
    DragSnapHaptics(controller.drag?.slot)
    LaunchedEffect(controller.isDragging) {
        if (controller.isDragging) controller.autoScroll()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        val drag = controller.drag ?: return@Box
        val fill = eventFill(drag.event.color, dark, soften)
        val title = drag.event.title.ifBlank { stringResource(R.string.event_untitled) }
        val label = "${formatMinuteOfDay(drag.startMin, use24Hour, locale)}–" +
            formatMinuteOfDay(drag.endMin.coerceAtMost(MINUTES_PER_DAY), use24Hour, locale)
        Box(
            modifier = Modifier
                .offset {
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
                    if (!reduceMotion) {
                        scaleX = 1.02f
                        scaleY = 1.02f
                    }
                    shadowElevation = 8.dp.toPx()
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
