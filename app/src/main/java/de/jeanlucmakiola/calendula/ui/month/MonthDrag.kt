package de.jeanlucmakiola.calendula.ui.month

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.datetime.LocalDate
import kotlin.math.abs

/** One week row's live geometry, republished on every layout while it is on screen. */
class MonthRowGeometry(
    val days: List<LocalDate>,
    /** The row's day-column box — the space chip offsets are measured in. */
    val cell: LayoutCoordinates,
    /** The lane band inside that box, where the chips themselves are seated. */
    val band: LayoutCoordinates?,
    val columnWidthPx: Float,
    val laneHeightPx: Float,
    val laneCount: Int,
    /**
     * Whether the columns are laid out right-to-left. Pointer coordinates are
     * never mirrored but the grid is, so the mapping has to flip with it.
     */
    val isRtl: Boolean,
    /** What this row currently draws in [lane] of column `col`, or null. */
    val chipAt: (col: Int, lane: Int) -> EventInstance?,
) {
    /** Root top-left of the chip seated in [lane] of column [col]. */
    fun seat(col: Int, lane: Int): Offset? {
        val band = band?.takeIf { it.isAttached } ?: return null
        val origin = band.positionInRoot()
        val column = if (isRtl) days.lastIndex - col else col
        return Offset(origin.x + column * columnWidthPx, origin.y + lane * laneHeightPx)
    }

    /**
     * Where this row seats [event] on [date], or null when it doesn't hold it —
     * the day isn't in this week, or the chip went into the day's "+N" overflow.
     */
    fun seatOf(event: EventInstance, date: LocalDate): Offset? {
        val col = days.indexOf(date).takeIf { it >= 0 } ?: return null
        val lane = (0 until laneCount).firstOrNull { lane ->
            chipAt(col, lane)?.let { isSameEvent(it, event) } == true
        } ?: return null
        return seat(col, lane)
    }
}

/**
 * Whether [chip] is the moved event [moved] as the grid now holds it. Neither id
 * alone will do: re-read instances get new instance ids, and a single-occurrence
 * move writes a new event id — the title survives both.
 */
private fun isSameEvent(chip: EventInstance, moved: EventInstance): Boolean =
    chip.eventId == moved.eventId || chip.title == moved.title

/** A chip in flight, in root coordinates so it can be drawn in an overlay. */
data class MonthChipDrag(
    val event: EventInstance,
    /** The day whose column the chip was grabbed in — the drag is a delta from it. */
    val grabDate: LocalDate,
    val targetDate: LocalDate?,
    val topLeftInRoot: Offset,
    val sizePx: IntSize,
)

/** Where a finished chip drag asks its event to go, as a whole-day shift. */
data class MonthChipDrop(
    val event: EventInstance,
    val grabDate: LocalDate,
    val targetDate: LocalDate,
)

/**
 * Hoisted drag state for the month grid (#68). Every visible week row registers
 * itself so a chip can be carried across rows, the target resolved against
 * whichever registered row the finger is over. Rows are keyed by an identity
 * token rather than a date: the continuous style can show the same week twice.
 */
@Stable
class MonthDragController {
    private val rows = LinkedHashMap<Any, MonthRowGeometry>()

    var drag: MonthChipDrag? by mutableStateOf(null)
        private set

    /**
     * A dropped chip, held on the day it landed on while the write runs — the
     * grid behind it still shows the old day until the query re-reads.
     */
    var settling: MonthChipDrag? by mutableStateOf(null)
        private set

    /**
     * Which chip is lifted, and whether anything is. Separate snapshot state from
     * [drag], which changes every frame, so the chips and rows that only need
     * "am I the ghost" don't recompose that often. Stays set through [settling].
     */
    var liftedInstanceId: Long? by mutableStateOf(null)
        private set

    /**
     * Where the grid itself now draws the drop, in root coordinates — published
     * by whichever week row ends up holding it. The copy is dropped wherever the
     * finger was while the grid seats it in a lane, so without this it would
     * cross-fade across the gap instead of gliding onto its seat.
     */
    var settledInRoot: Offset? by mutableStateOf(null)
        private set

    /**
     * Whether the drop's own chip is still standing in for the copy. Cleared at
     * [handOver] rather than [release], or the chip stays dim under a copy that
     * has already faded and brightens afterwards: a visible dip.
     */
    private var settledGhost: Boolean by mutableStateOf(false)

    /** Whether a finger is on a chip right now — [settling] is not dragging. */
    var isDragging: Boolean by mutableStateOf(false)
        private set

    /**
     * The last drop this controller made, kept past [release] for [beginUndo] —
     * the confirmation chip carries Undo long after the copy is gone.
     */
    private var undoable: MonthChipDrag? = null

    private var event: EventInstance? = null
    private var grabDate: LocalDate? = null
    private var grab = Offset.Zero
    private var pointer = Offset.Zero
    private var sizePx = IntSize.Zero

    fun putRow(token: Any, geometry: MonthRowGeometry) {
        rows[token] = geometry
    }

    fun removeRow(token: Any) {
        rows.remove(token)
    }

    fun begin(
        event: EventInstance,
        grabDate: LocalDate,
        pointerInRoot: Offset,
        chipInRoot: Offset,
        size: IntSize,
    ) {
        this.event = event
        this.grabDate = grabDate
        settling = null
        isDragging = true
        liftedInstanceId = event.instanceId
        grab = pointerInRoot - chipInRoot
        pointer = pointerInRoot
        sizePx = size
        recompute()
    }

    fun move(pointerInRoot: Offset) {
        pointer = pointerInRoot
        recompute()
    }

    fun cancel() {
        event = null
        grabDate = null
        isDragging = false
        liftedInstanceId = null
        drag = null
        settling = null
        settledInRoot = null
        settledGhost = false
    }

    /**
     * Whether [days] holds the chip the grid has drawn for the settled drop —
     * the seat the copy is on its way to, which must ghost until it gets there.
     */
    fun isSettledChip(event: EventInstance, days: List<LocalDate>?): Boolean {
        val landed = settling?.takeIf { settledGhost } ?: return false
        if (days == null || landed.targetDate !in days) return false
        return isSameEvent(event, landed.event)
    }

    /**
     * Ask the row keyed [token] whether it now seats the settled chip. Both
     * copies of a week the continuous style shows twice will answer; the seat
     * nearest where the chip was let go is the one the finger was over.
     */
    fun noteSettled(token: Any) {
        val landed = settling ?: return
        val target = landed.targetDate ?: return
        val at = rows[token]?.seatOf(landed.event, target) ?: return
        val current = settledInRoot
        val closer = current == null ||
            abs(at.y - landed.topLeftInRoot.y) < abs(current.y - landed.topLeftInRoot.y)
        if (closer) settledInRoot = at
    }

    /**
     * Put a chip on the journey an undo's inverse write is about to make, so it
     * travels back rather than teleporting. Writes nothing itself. False when
     * there is no drop of this controller's left to undo, or the grid doesn't
     * seat the moved event where the chip would have to start from.
     */
    fun beginUndo(): Boolean {
        val last = undoable ?: return false
        undoable = null
        val from = last.targetDate ?: return false
        val at = rows.values.firstNotNullOfOrNull { it.seatOf(last.event, from) } ?: return false
        settling = last.copy(grabDate = from, targetDate = last.grabDate, topLeftInRoot = at)
        liftedInstanceId = last.event.instanceId
        settledGhost = true
        settledInRoot = null
        return true
    }

    /**
     * End the drag, handing back where it landed — null when it never resolved,
     * or when it landed back on the day it came from. A real drop keeps its chip
     * on the target day as [settling] until [release], slid over to that day's
     * column so it lands where the grid is about to draw it.
     */
    fun finish(): MonthChipDrop? {
        val landed = drag
        val left = landed?.targetDate?.let(::columnLeft)
        cancel()
        val target = landed?.targetDate?.takeIf { it != landed.grabDate } ?: return null
        settling = landed.copy(
            topLeftInRoot = Offset(left ?: landed.topLeftInRoot.x, landed.topLeftInRoot.y),
        )
        liftedInstanceId = landed.event.instanceId
        settledGhost = true
        undoable = settling
        return MonthChipDrop(landed.event, landed.grabDate, target)
    }

    /**
     * Stop ghosting the source while the copy is still dissolving — un-ghosting
     * at [release] instead leaves the chip dim and then brightens it.
     */
    fun handOver() {
        liftedInstanceId = null
        settledGhost = false
    }

    /** Drop the copy, once it has faded into the grid's own chip. */
    fun release() {
        settling = null
        settledInRoot = null
        handOver()
    }

    /** Root x of [date]'s column, in whichever visible row shows that day. */
    private fun columnLeft(date: LocalDate): Float? = rows.values.firstNotNullOfOrNull { row ->
        val index = row.days.indexOf(date).takeIf { it >= 0 } ?: return@firstNotNullOfOrNull null
        val bounds = row.cell.takeIf { it.isAttached }?.boundsInRoot()
            ?: return@firstNotNullOfOrNull null
        val column = if (row.isRtl) row.days.lastIndex - index else index
        bounds.left + column * row.columnWidthPx
    }

    private fun recompute() {
        val event = event ?: return
        val grabbed = grabDate ?: return
        val resolved = rows.values.firstNotNullOfOrNull { row ->
            val bounds = row.cell.takeIf { it.isAttached }?.boundsInRoot()
                ?: return@firstNotNullOfOrNull null
            if (!bounds.contains(pointer) || row.columnWidthPx <= 0f) {
                null
            } else {
                val column = ((pointer.x - bounds.left) / row.columnWidthPx)
                    .toInt()
                    .coerceIn(0, row.days.lastIndex)
                row.days[if (row.isRtl) row.days.lastIndex - column else column]
            }
        }
        drag = MonthChipDrag(
            event = event,
            grabDate = grabbed,
            // Off the grid entirely: keep the last cell it was over, so a
            // wobble past the edge doesn't drop the target.
            targetDate = resolved ?: drag?.targetDate,
            topLeftInRoot = pointer - grab,
            sizePx = sizePx,
        )
    }
}

@Composable
fun rememberMonthDragController(): MonthDragController = remember { MonthDragController() }

val LocalMonthDrag = compositionLocalOf<MonthDragController?> { null }
