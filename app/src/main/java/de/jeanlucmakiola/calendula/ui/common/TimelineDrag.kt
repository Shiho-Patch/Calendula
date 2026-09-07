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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
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
    /**
     * Where the dragged *block's* top edge now sits, in minutes from [date]'s
     * midnight. The gesture's own quantity — what snaps, what the haptics tick
     * on, and what the drop is expressed in. Not the event's start: for the tail
     * of an event that began the day before, the two are a clip offset apart.
     */
    val startMin: Int,
    /** The event's new start, from [date]'s midnight — negative if it began earlier. */
    val eventStartMin: Int,
    val eventSpanMin: Int,
    /** The event as the grid would draw it, one piece per day column it covers. */
    val pieces: List<TimelineDragPiece>,
    /**
     * Lines the block the finger picked up drew its title over, zero for one too
     * short to have drawn it at all. The floating copy is that block's size, so
     * it has to spend its height the same way or the text re-wraps under the
     * finger and snaps back on drop (#267).
     */
    val titleLines: Int,
    /**
     * True when [pieces] is only [edgeDragSlice]'s stand-in — the event has left
     * every column this timeline shows. A hint under the finger, not where the
     * drop lands, so it is dropped once the gesture ends ([finish]).
     */
    val edgeOnly: Boolean = false,
) {
    /** What the snap haptics key off: one tick per changed slot, not per frame. */
    val slot: Pair<LocalDate, Int> get() = date to startMin
}

/**
 * One day column's share of a dragged event, ready to draw. Both halves of an
 * event crossing midnight are previewed, whichever half the finger picked up:
 * showing only the held one made the event read as growing rather than moving,
 * since the day beside it still stood at the block's old extent (#253).
 */
data class TimelineDragPiece(
    val topLeftInRoot: Offset,
    val sizePx: IntSize,
    val continuesBefore: Boolean,
    val continuesAfter: Boolean,
)

/**
 * How a run of [spanMin] minutes beginning [eventStartMin] minutes into a day —
 * negative when it began on an earlier one — falls across day columns, as
 * offsets from that day. Every slice but the first and last is a whole day, and
 * the flags say which edges are cuts rather than the event's own ends.
 *
 * [within] bounds the day offsets that have a column to draw in, so a very long
 * event doesn't slice days no one can see.
 */
internal fun dragSlices(
    eventStartMin: Int,
    spanMin: Int,
    within: IntRange? = null,
): List<DragSlice> {
    val span = spanMin.coerceAtLeast(0)
    val end = eventStartMin + span
    val days = dragDayRange(eventStartMin, spanMin)
    val first = days.first
    val last = days.last
    val from = maxOf(first, within?.first ?: first)
    val to = minOf(last, within?.last ?: last)
    if (from > to) return emptyList()
    return (from..to).map { day ->
        val dayStart = day * MINUTES_PER_DAY
        val sliceStart = maxOf(eventStartMin - dayStart, 0)
        val sliceEnd = minOf(end - dayStart, MINUTES_PER_DAY)
        DragSlice(
            dayOffset = day,
            startMin = sliceStart,
            spanMin = sliceEnd - sliceStart,
            continuesBefore = day > first,
            continuesAfter = day < last,
        )
    }
}

/**
 * The one piece to draw when a drag has carried the whole event clear of the
 * columns this timeline shows, so [dragSlices] has nothing to hand back — where a
 * short clipped tail lands at its [dragFloorMin]: the event then belongs to the
 * day before, which Day view has no column for, and the copy would blink out from
 * under the finger mid-gesture. Pinned to the top of the nearest visible column
 * at minimum height instead, cut at that edge, so it reads as having slid off the
 * top. Null whenever a real slice exists. The drop is unaffected either way — it
 * reads [TimelineDrag.startMin], not the pieces.
 */
internal fun edgeDragSlice(eventStartMin: Int, spanMin: Int, within: IntRange): DragSlice? {
    if (dragDayRange(eventStartMin, spanMin).last >= within.first) return null
    return DragSlice(
        dayOffset = within.first,
        startMin = 0,
        spanMin = 0,
        continuesBefore = true,
        continuesAfter = false,
    )
}

/**
 * The day offsets a run of [spanMin] minutes beginning [eventStartMin] covers,
 * before any clipping to what a timeline shows. One rule, so [dragSlices] and
 * [edgeDragSlice] can't drift apart on where an event's days begin and end.
 */
private fun dragDayRange(eventStartMin: Int, spanMin: Int): IntRange {
    val end = eventStartMin + spanMin.coerceAtLeast(0)
    val first = eventStartMin.floorDiv(MINUTES_PER_DAY)
    // A zero-length event still has a block, on the one day it sits in.
    return first..maxOf((end - 1).floorDiv(MINUTES_PER_DAY), first)
}

/**
 * How far above its day's midnight a block's top edge may be dragged. Zero for
 * an ordinary block, whose top edge is the event's own start. A tail clipped at
 * midnight has to go lower: its top edge *is* midnight and stays there however
 * much earlier the event moves, so pinning it at zero made dragging the second
 * half earlier a no-op (#253). It may rise until the event's end reaches one
 * snap step into this day — or by a single step, for a tail too short to afford
 * even that, which then leaves the day for the one before it.
 */
internal fun dragFloorMin(clipOffsetMin: Int, eventSpanMin: Int): Int =
    if (clipOffsetMin > 0) {
        minOf(-DRAG_SNAP_MINUTES, DRAG_SNAP_MINUTES + clipOffsetMin - eventSpanMin)
    } else {
        0
    }

/** One day's share of a dragged event, before it is placed on screen. */
internal data class DragSlice(
    val dayOffset: Int,
    val startMin: Int,
    val spanMin: Int,
    val continuesBefore: Boolean,
    val continuesAfter: Boolean,
)

/**
 * Where a finished drag asks its event to go. [date]/[startMin] are where the
 * dragged *block's* top edge landed, which is the event's own start only for a
 * block that isn't clipped — see [clipOffsetMin] and [startInstant].
 */
data class TimelineDrop(
    val event: EventInstance,
    val date: LocalDate,
    val startMin: Int,
    /** Minutes from the event's start to the dragged block's top edge. */
    val clipOffsetMin: Int = 0,
)

/**
 * The timeline's live geometry, republished on every layout. Plain fields rather
 * than snapshot state: it changes on every scroll frame, and the drag loop reads
 * it per frame anyway.
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
     * never mirrored but the grid is, so the mapping has to flip with it.
     */
    var isRtl: Boolean = false
}

/**
 * Hoisted drag state for one timeline (#68). It lives above the per-page
 * `AnimatedContent`, so a page change mid-drag can't strand a ghost, and the
 * block it renders is drawn in an overlay, clear of the day column's clip.
 */
@Stable
class TimelineDragController {
    val geometry = TimelineGeometry()

    var drag: TimelineDrag? by mutableStateOf(null)
        private set

    /**
     * A dropped block, held at the slot it landed on while the write runs — the
     * grid behind it still shows the old time until the query re-reads.
     */
    var settling: TimelineDrag? by mutableStateOf(null)
        private set

    /**
     * Which block is lifted, and whether anything is. Separate snapshot state
     * from [drag], which changes every frame, so blocks that only need "am I the
     * ghost" don't recompose that often. Stays set through [settling].
     */
    var liftedInstanceId: Long? by mutableStateOf(null)
        private set

    /**
     * The event row and start instant the settled drop came from. The instance id
     * alone can't identify the source for the length of the write: the provider
     * regenerates `Instances` rows, so a re-read carrying the *old* time can
     * arrive under a new instance id. Matching the start instant rather than a
     * single day and slot keeps *both* halves of an event that crosses midnight
     * ghosted until the write lands (#253).
     */
    private var settledOrigin: Pair<Long, Instant>? by mutableStateOf(null)

    /** Whether a finger is on a block right now — [settling] is not dragging. */
    var isDragging: Boolean by mutableStateOf(false)
        private set

    /**
     * Whether the grid itself now draws the settled drop at its landing slot.
     * The copy may only be handed back once this is true, or the source ghost
     * flashes back to full opacity in its old slot.
     */
    var settledOnGrid: Boolean by mutableStateOf(false)
        private set

    /**
     * The abandoned-move counter, read at the drop so the mark can't be a
     * composition behind the tick it is compared against.
     */
    var abandonedTicks: StateFlow<Int>? = null

    /** What [abandonedTicks] stood at when the settling drop was let go. */
    var settledAbandonedMark: Int = 0
        private set

    private var source: TimedBlock? = null
    private var grab = Offset.Zero
    private var pointer = Offset.Zero

    /**
     * How far the picked-up block's top edge sits past the event's own start —
     * nonzero only for a block clipped at midnight, which is the tail of an
     * event that began the day before (#253). The gesture tracks the block the
     * finger is on; this is what turns where it lands back into an event start.
     */
    private var clipOffsetMin = 0

    /** What the picked-up block drew its title over — see [TimelineDrag.titleLines]. */
    private var titleLines = 0

    /**
     * The slot the block already occupied when it was picked up — not the same
     * as its start, since the target snaps to the grid (09:07 lifts to 09:00).
     */
    private var originSlot: Pair<LocalDate, Int>? = null

    fun begin(
        block: TimedBlock,
        clipOffsetMin: Int,
        titleLines: Int,
        pointerInRoot: Offset,
        blockInRoot: Offset,
    ) {
        source = block
        this.clipOffsetMin = clipOffsetMin
        this.titleLines = titleLines
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
        clipOffsetMin = 0
        titleLines = 0
        isDragging = false
        liftedInstanceId = null
        originSlot = null
        drag = null
        settling = null
        settledOnGrid = false
        settledOrigin = null
    }

    /**
     * Whether [block] is part of the event whose copy is in flight, and so must
     * stay a ghost: the instance the finger picked up, or once dropped whatever
     * the re-read now carries at the time it came from — both halves of it, for
     * an event that crosses midnight (#253).
     */
    fun ghosts(block: TimedBlock): Boolean {
        if (liftedInstanceId == null) return false
        if (block.event.instanceId == liftedInstanceId) return true
        val (eventId, start) = settledOrigin ?: return false
        return block.event.eventId == eventId && block.event.start == start
    }

    /**
     * What a day column now holds, so a settled drop can tell when the grid has
     * caught up with it. Matched on the landing slot plus either the event row
     * or its title, since a single-occurrence move writes a new `eventId`. A
     * blank title matches nothing: every untitled event has one (#253).
     */
    fun noteGrid(date: LocalDate, blocks: List<TimedBlock>) {
        val landed = settling ?: return
        if (settledOnGrid) return
        // Where this day would draw it, which for a drop clipped at midnight is
        // the day's own start rather than the edge the finger held (#253) — and
        // which for a tail dragged clear of its day is the day before it.
        val offset = (date.toEpochDays() - landed.date.toEpochDays()).toInt()
        val slice = dragSlices(landed.eventStartMin, landed.eventSpanMin, offset..offset)
            .firstOrNull() ?: return
        settledOnGrid = blocks.any { block ->
            block.startMin == slice.startMin &&
                (
                    block.event.eventId == landed.event.eventId ||
                        (
                            landed.event.title.isNotBlank() &&
                                block.event.title == landed.event.title
                            )
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
        val clipOffset = clipOffsetMin
        cancel()
        if (landed == null || landed.slot == origin) return null
        // An edge piece is a hint for the finger, not a preview of the landing
        // slot: the event belongs to a day this timeline has no column for, so
        // [noteGrid] can never seat it and it would sit at the top of today for
        // the whole settle timeout, long after the event moved off-screen.
        settling = if (landed.edgeOnly) landed.copy(pieces = emptyList()) else landed
        liftedInstanceId = landed.event.instanceId
        settledOrigin = landed.event.eventId to landed.event.start
        settledAbandonedMark = abandonedTicks?.value ?: 0
        return TimelineDrop(landed.event, landed.date, landed.startMin, clipOffset)
    }

    /**
     * Stop ghosting the source — the grid draws the drop itself by now — while
     * the copy is still dissolving. Un-ghosting at [release] instead would leave
     * the block dim under the fading copy and then brighten it: a visible dip.
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
        val eventSpan = (block.event.end - block.event.start).inWholeMinutes.toInt()
            .coerceAtLeast(0)
        val floor = dragFloorMin(clipOffsetMin, eventSpan)
        val startMin = snapped.coerceIn(floor, MINUTES_PER_DAY - DRAG_SNAP_MINUTES)
        val eventStartMin = startMin - clipOffsetMin
        val column = ((pointer.x - origin.x) / columnPx).toInt().coerceIn(0, days.lastIndex)
        val dayIndex = if (geometry.isRtl) days.lastIndex - column else column
        val visible = -dayIndex..days.lastIndex - dayIndex
        val onGrid = dragSlices(eventStartMin, eventSpan, within = visible)
        val slices = onGrid.ifEmpty {
            listOfNotNull(edgeDragSlice(eventStartMin, eventSpan, visible))
        }
        drag = TimelineDrag(
            event = block.event,
            date = days[dayIndex],
            startMin = startMin,
            eventStartMin = eventStartMin,
            eventSpanMin = eventSpan,
            titleLines = titleLines,
            // Bounded to the columns this timeline actually shows: a day it
            // doesn't has no piece to draw. The write is unaffected.
            pieces = slices
                .map { slice ->
                    val day = dayIndex + slice.dayOffset
                    val col = if (geometry.isRtl) days.lastIndex - day else day
                    TimelineDragPiece(
                        topLeftInRoot = Offset(
                            x = origin.x + col * columnPx,
                            y = origin.y + slice.startMin / 60f * hourPx,
                        ),
                        sizePx = IntSize(
                            (columnPx - geometry.columnGapPx).roundToInt(),
                            maxOf(
                                slice.spanMin / 60f * hourPx,
                                MIN_EVENT_FRACTION * hourPx,
                            ).roundToInt(),
                        ),
                        continuesBefore = slice.continuesBefore,
                        continuesAfter = slice.continuesAfter,
                    )
                },
            edgeOnly = onGrid.isEmpty(),
        )
    }

    /**
     * Hold the scroll for the whole drag and nudge it once per frame while the
     * finger sits near an edge. One `scroll` call, since a loop of
     * `animateScrollBy` would re-acquire the mutex and cancel the last.
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
    controller.abandonedTicks = LocalEventMove.current?.abandoned
    return controller
}

/**
 * How far this block's top edge sits past its event's start, in minutes — zero
 * for a block that begins on [day], and the part already run for the tail of an
 * event that started earlier, whose top edge is midnight rather than the event's
 * start. Dragging such a tail moves the event by where *its own* top edge lands,
 * so the offset has to come back off at the drop (#253).
 */
fun TimedBlock.clipOffsetMinutes(day: LocalDate, zone: TimeZone): Int {
    val start = event.start.toLocalDateTime(zone)
    val days = (day.toEpochDays() - start.date.toEpochDays()).toInt()
    val minutes = days * MINUTES_PER_DAY + startMin - start.time.toSecondOfDay() / 60
    return minutes.coerceAtLeast(0)
}

/**
 * True when the event began before [day] — this block is its tail, cut at
 * midnight rather than starting where the event does.
 */
fun TimedBlock.continuesBefore(day: LocalDate, zone: TimeZone): Boolean =
    event.start < day.atStartOfDayIn(zone)

/** True when the event runs past [day]'s midnight, so this block is cut there. */
fun TimedBlock.continuesAfter(day: LocalDate, zone: TimeZone): Boolean =
    event.end > day.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)

/**
 * The instant a drop asks for — the *event's* new start, so the tail of an
 * overnight event drops back onto the day its own start belongs to. Read in the
 * zone the timeline is drawn in. A drop into a spring-forward gap has no instant
 * of its own; `toInstant` resolves it forward by the missing hour, and the block
 * visibly settles there.
 */
fun TimelineDrop.startInstant(zone: TimeZone): Instant {
    // Wall clock throughout, and past this day at either end: a block's top edge
    // can be dragged above its midnight and a tail's below the next one, so the
    // start is resolved as a day plus a minute of it rather than as an offset
    // from an instant — which would shift by an hour across a DST boundary.
    val fromMidnight = startMin - clipOffsetMin
    val day = date.plus(fromMidnight.floorDiv(MINUTES_PER_DAY), DateTimeUnit.DAY)
    val minute = fromMidnight.mod(MINUTES_PER_DAY)
    return LocalDateTime(day, LocalTime(minute / 60, minute % 60)).toInstant(zone)
}

/**
 * A beat after the grid draws the drop, so its own block is under way before the
 * copy starts dissolving.
 */
const val SETTLE_GRACE_MILLIS: Long = 60L

/**
 * How long to wait for a grid that never confirms the drop — it landed on a day
 * this timeline doesn't show, or the write failed. The copy has to go either way.
 */
private const val SETTLE_TIMEOUT_MILLIS = 900L

/**
 * The hand-over: the copy dissolves over this while the grid's own block slides
 * in under it. Kept short, because a full-width copy sits over any neighbour it
 * now shares a lane with until it is gone.
 */
const val SETTLE_FADE_MILLIS: Int = 250

/**
 * The floating block, drawn over the whole calendar so it is free of the day
 * column's clip and of the scroll viewport's rounded corners.
 */
@Composable
fun TimelineDragOverlay(controller: TimelineDragController, modifier: Modifier = Modifier) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val dark = isSystemInDarkTheme()
    val use24Hour = LocalUse24HourFormat.current
    val locale = currentLocale()
    val reduceMotion = rememberReduceMotion()
    val moveInFlight = moveInFlight()

    // Both live here rather than beside the controller: they read [drag], which
    // changes every frame, and this composable is the one that is meant to.
    DragSnapHaptics(controller.drag?.slot)
    LaunchedEffect(controller.isDragging) {
        if (controller.isDragging) controller.autoScroll()
    }
    // Hold the landed copy until the write is done — including the time a
    // recurring drop's scope dialog is up — and then until the grid draws the
    // drop itself.
    var handingOver by remember(controller.settling) { mutableStateOf(false) }
    val abandoned = abandonedMoves()
    LaunchedEffect(controller.settling, moveInFlight, controller.settledOnGrid, abandoned) {
        if (controller.settling == null || moveInFlight) return@LaunchedEffect
        // Past the mark this drop was let go on, it was abandoned: nothing will
        // land and there is nothing to wait for.
        if (abandoned == controller.settledAbandonedMark) {
            delay(if (controller.settledOnGrid) SETTLE_GRACE_MILLIS else SETTLE_TIMEOUT_MILLIS)
        }
        handingOver = true
        controller.handOver()
        delay(SETTLE_FADE_MILLIS.toLong())
        controller.release()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // A copy of a block still in the tree behind it; announcing it again
            // would duplicate the event for the drag's duration.
            .clearAndSetSemantics { }
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        val drag = controller.drag ?: controller.settling ?: return@Box
        // Landed: the copy sinks back to the grid's own plane while the write runs.
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
        val paint = eventPaint(drag.event, dark)
        val title = drag.event.title.ifBlank { stringResource(R.string.event_untitled) }
        // Read off the event rather than the held block, so grabbing either half
        // of an event that crosses midnight names the same hours. An end past
        // midnight wraps rather than saturating, so a four-hour event dragged to
        // 22:00 reads "22:00–02:00" and not "22:00–24:00".
        val startMin = drag.eventStartMin.mod(MINUTES_PER_DAY)
        val rawEnd = startMin + drag.eventSpanMin
        val endMin = if (rawEnd > MINUTES_PER_DAY) rawEnd % MINUTES_PER_DAY else rawEnd
        val label = "${formatMinuteOfDay(startMin, use24Hour, locale)}–" +
            formatMinuteOfDay(endMin, use24Hour, locale)
        // The timeline's own bounds. The copy is drawn over the whole calendar
        // so no column clip or rounded corner cuts it, but it still belongs to
        // the timeline: a piece a whole day tall reaches far past both ends of
        // the viewport, and unclipped it paints over the headers above (#267).
        val port = controller.geometry.viewport?.takeIf { it.isAttached }?.boundsInRoot()
        // How much of each piece the viewport actually shows. A multi-day event
        // has a piece per day, and the widest one is a full 24 hours — taller
        // than the screen, its top at a midnight scrolled out of sight. Picking
        // the range's piece by raw height put it there, off the top of the
        // timeline; picking by *visible* height puts it where it can be read.
        val shown = drag.pieces.map { piece ->
            if (port == null) {
                piece.sizePx.height.toFloat()
            } else {
                val top = maxOf(piece.topLeftInRoot.y, port.top)
                val bottom = minOf(piece.topLeftInRoot.y + piece.sizePx.height, port.bottom)
                bottom - top
            }
        }
        val labelled = shown.indices.maxByOrNull { shown[it] }
        Box(
            modifier = if (port == null) {
                Modifier
            } else {
                Modifier
                    .absoluteOffset {
                        IntOffset(
                            (port.left - origin.x).roundToInt(),
                            (port.top - origin.y).roundToInt(),
                        )
                    }
                    .size(
                        width = with(density) { port.width.toDp() },
                        height = with(density) { port.height.toDp() },
                    )
                    .clipToBounds()
            },
        ) {
            val pieceOrigin = port?.topLeft ?: origin
            drag.pieces.forEachIndexed { index, piece ->
                DragCopy(
                    topLeftInRoot = piece.topLeftInRoot,
                    overlayOrigin = pieceOrigin,
                    sizePx = piece.sizePx,
                    paint = paint,
                    shape = timedBlockShape(piece.continuesBefore, piece.continuesAfter),
                    cuts = timedBlockCuts(piece.continuesBefore, piece.continuesAfter),
                    lift = lift,
                    alpha = copyAlpha,
                    title = title,
                    titleLines = drag.titleLines,
                    // Once for the whole event: repeated, it would name it per day.
                    label = label.takeIf { index == labelled },
                )
            }
        }
    }
}

/** One piece of a dragged block, floating over the calendar. */
@Composable
private fun DragCopy(
    topLeftInRoot: Offset,
    overlayOrigin: Offset,
    sizePx: IntSize,
    paint: EventPaint,
    shape: RoundedCornerShape,
    cuts: ChipCuts,
    lift: Float,
    alpha: Float,
    title: String,
    titleLines: Int,
    label: String?,
) {
    val density = LocalDensity.current
    val width = with(density) { sizePx.width.toDp() }
    val height = with(density) { sizePx.height.toDp() }
    val textWidth = width - (BLOCK_OUTER_INSET + BLOCK_TEXT_PADDING) * 2
    val titleLineHeight = with(density) {
        MaterialTheme.typography.labelMedium.lineHeight.toDp()
    }
    val timeLineHeight = with(density) {
        MaterialTheme.typography.labelSmall.lineHeight.toDp()
    }
    // The block's size, spent the block's way — text sits at the top as it does
    // on the block, so the copy hands back to the grid without shifting (#267).
    // The title is served in full first and the range lives off what is left:
    // the hour gutter down the side still says where the copy sits, so the
    // range is the half that can afford to go.
    val available = height - BLOCK_TEXT_INSET * 2
    val titleBudget = (available / titleLineHeight).toInt().coerceAtLeast(0)
    val lines = titleLines.coerceAtMost(titleBudget)
    val left = available - titleLineHeight * lines
    val showTime = label != null && left >= timeLineHeight
    val timeMaxLines = if (showTime) {
        blockTimeLines(label!!, textWidth, left - timeLineHeight)
    } else {
        1
    }
    Box(
        modifier = Modifier
            // Absolute: these are root coordinates, and the direction-aware
            // offset would mirror them across the screen in an RTL layout.
            .absoluteOffset {
                IntOffset(
                    (topLeftInRoot.x - overlayOrigin.x).roundToInt(),
                    (topLeftInRoot.y - overlayOrigin.y).roundToInt(),
                )
            }
            .size(width = width, height = height)
            .padding(horizontal = BLOCK_OUTER_INSET)
            .graphicsLayer {
                // Anchored at the top edge, not the middle: scaled about the
                // centre, the 2% lift raises the top by 1% of the height — a few
                // pixels on an hour, but a steady upward creep of the text as a
                // dragged multi-day piece grows, and a shear against the
                // timeline's clip. It snapped back when the lift animated out
                // on drop (#267).
                transformOrigin = TransformOrigin(0.5f, 0f)
                scaleX = 1f + 0.02f * lift
                scaleY = 1f + 0.02f * lift
                shadowElevation = 8.dp.toPx() * lift
                this.alpha = alpha
                this.shape = shape
                clip = false
            }
            .eventSurface(paint, shape, cuts)
            .padding(horizontal = BLOCK_TEXT_PADDING, vertical = BLOCK_TEXT_INSET),
    ) {
        Column {
            if (lines > 0) {
                BlockTitle(
                    title = title,
                    maxLines = lines,
                    color = paint.titleInk,
                    textDecoration = paint.decoration,
                    fontWeight = paint.titleWeight,
                )
            }
            if (showTime) {
                val overflow = eventTitleOverflow(singleLine = timeMaxLines == 1)
                Text(
                    text = label,
                    // As the block it lifted off sets its own time (#219).
                    style = MaterialTheme.typography.labelSmall.asEventTime(),
                    maxLines = timeMaxLines,
                    overflow = overflow.overflow,
                    softWrap = overflow.softWrap,
                    color = paint.secondaryInk,
                )
            }
        }
    }
}
