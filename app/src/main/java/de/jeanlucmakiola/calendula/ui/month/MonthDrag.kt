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
import androidx.compose.ui.unit.IntSize
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.datetime.LocalDate
import kotlin.math.abs

/** One week row's live geometry, republished on every layout while it is on screen. */
class MonthRowGeometry(
    val days: List<LocalDate>,
    /** The row's day-column box — the space chip offsets are measured in. */
    val cell: LayoutCoordinates,
    val columnWidthPx: Float,
    /**
     * Whether the columns are laid out right-to-left. Pointer coordinates are
     * never mirrored, but the grid is, so the leftmost column is the *last* day
     * in Arabic.
     */
    val isRtl: Boolean,
)

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
 * Hoisted drag state for the month grid (#68).
 *
 * Every visible week row registers itself, so a chip can be carried across rows:
 * the pointer node that took the press keeps receiving events after the finger
 * has left its own bounds, and the target is then resolved against whichever
 * registered row the finger is actually over. Rows are keyed by an identity token
 * rather than by their date, because the continuous style can show the same week
 * twice — once in each adjoining month.
 */
@Stable
class MonthDragController {
    private val rows = LinkedHashMap<Any, MonthRowGeometry>()

    var drag: MonthChipDrag? by mutableStateOf(null)
        private set

    /**
     * A dropped chip, held on the day it landed on while the write runs. The grid
     * behind it still shows the old day until the provider notifies and the query
     * re-reads, so releasing at drop time would snap the event back for the
     * length of the write.
     */
    var settling: MonthChipDrag? by mutableStateOf(null)
        private set

    /**
     * Which chip is lifted, and whether anything is. Separate snapshot state from
     * [drag] on purpose: [drag] changes on every frame, while the chips and rows
     * that only need "am I the ghost" / "must I stop clipping" must not. Stays
     * set through [settling], so the source never reappears under the copy.
     */
    var liftedInstanceId: Long? by mutableStateOf(null)
        private set

    /**
     * Where the grid itself now draws the drop, in root coordinates — published
     * by whichever week row ends up holding it. The copy is dropped wherever the
     * finger was, but the grid seats it in a lane; without this the copy waits
     * out a flat hold beside the chip the grid has already drawn and then
     * cross-fades across the gap, which reads as the event taking its time to
     * arrive. With it the copy glides onto its seat and hands over there.
     */
    var settledInRoot: Offset? by mutableStateOf(null)
        private set

    /**
     * Whether the drop's own chip is still standing in for the copy. Cleared at
     * [handOver] rather than at [release], for the same reason [liftedInstanceId]
     * is: held to the end it would keep the chip dim under a copy that has
     * already faded, and brighten it afterwards — a dip the eye reads as the
     * event flickering.
     */
    private var settledGhost: Boolean by mutableStateOf(false)

    /** Whether a finger is on a chip right now — [settling] is not dragging. */
    var isDragging: Boolean by mutableStateOf(false)
        private set

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
     * Matched on the landing day plus either the event row or its title: a
     * single-occurrence move writes an exception row with a new `eventId`, and
     * the provider hands re-read instances new ids, so neither alone identifies
     * the chip the drop became.
     */
    fun isSettledChip(event: EventInstance, days: List<LocalDate>?): Boolean {
        val landed = settling?.takeIf { settledGhost } ?: return false
        if (days == null || landed.targetDate !in days) return false
        return event.eventId == landed.event.eventId || event.title == landed.event.title
    }

    /**
     * Publish a seat. The continuous style can show the same week twice, once in
     * each adjoining month, so both copies of the landing row offer one: the one
     * nearest where the chip was dropped is the one the finger was over.
     */
    fun noteSettled(topLeftInRoot: Offset) {
        val landed = settling ?: return
        val current = settledInRoot
        val closer = current == null ||
            abs(topLeftInRoot.y - landed.topLeftInRoot.y) < abs(current.y - landed.topLeftInRoot.y)
        if (closer) settledInRoot = topLeftInRoot
    }

    /**
     * End the drag. A real drop keeps its chip on the target day as [settling]
     * until [release] — slid over to that day's column, so it lands where the
     * grid is about to draw it rather than wherever the finger happened to be.
     */
    fun finish(): MonthChipDrop? {
        val landed = drag
        val left = landed?.targetDate?.let(::columnLeft)
        cancel()
        val target = landed?.targetDate ?: return null
        settling = landed.copy(
            topLeftInRoot = Offset(left ?: landed.topLeftInRoot.x, landed.topLeftInRoot.y),
        )
        liftedInstanceId = landed.event.instanceId
        settledGhost = true
        return MonthChipDrop(landed.event, landed.grabDate, target)
    }

    /**
     * Stop ghosting the source while the copy is still dissolving — un-ghosting
     * only at [release] leaves the chip under it dim and then brightens it, a
     * dip the eye reads as the event flickering.
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
            // Off the grid entirely (the gutter, the header): keep the last cell
            // it was over, so a wobble past the edge doesn't drop the target.
            targetDate = resolved ?: drag?.targetDate,
            topLeftInRoot = pointer - grab,
            sizePx = sizePx,
        )
    }
}

@Composable
fun rememberMonthDragController(): MonthDragController = remember { MonthDragController() }

val LocalMonthDrag = compositionLocalOf<MonthDragController?> { null }
