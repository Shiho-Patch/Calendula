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

/** One week row's live geometry, republished on every layout while it is on screen. */
class MonthRowGeometry(
    val days: List<LocalDate>,
    /** The row's day-column box — the space chip offsets are measured in. */
    val cell: LayoutCoordinates,
    val columnWidthPx: Float,
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
     * Which chip is lifted, and whether anything is. Separate snapshot state from
     * [drag] on purpose: [drag] changes on every frame, while the chips and rows
     * that only need "am I the ghost" / "must I stop clipping" must not.
     */
    var liftedInstanceId: Long? by mutableStateOf(null)
        private set

    private var event: EventInstance? = null
    private var grabDate: LocalDate? = null
    private var grab = Offset.Zero
    private var pointer = Offset.Zero
    private var sizePx = IntSize.Zero

    val isDragging: Boolean get() = liftedInstanceId != null

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
        liftedInstanceId = null
        drag = null
    }

    fun finish(): MonthChipDrop? {
        val landed = drag
        cancel()
        val target = landed?.targetDate ?: return null
        return MonthChipDrop(landed.event, landed.grabDate, target)
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
                row.days[column]
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
