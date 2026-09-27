package de.jeanlucmakiola.calendula.ui.week

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlin.math.roundToInt

/** A day column's place across the timeline, in pixels from its start edge. */
@Immutable
data class ColumnSlot(val left: Float, val width: Float) {
    val right: Float get() = left + width
}

/**
 * [date]'s column on a page showing this span across [width] pixels, with [gap]
 * between columns. A date off the page carries on at the same pitch, off screen
 * on its side — where a view switch slides it in from, or out to.
 */
fun DaySpan.slot(date: LocalDate, width: Float, gap: Float): ColumnSlot {
    val pitch = (width + gap) / count
    return ColumnSlot(left = start.daysUntil(date) * pitch, width = pitch - gap)
}

/** The last day of the span. */
val DaySpan.end: LocalDate get() = start.plus(count - 1, DateTimeUnit.DAY)

/**
 * The timeline's columns: one page's, or — while switching between the day, the
 * multi-day and the week view (#184) — every day of both pages, each column
 * between where it sits in the one and where it sits in the other.
 *
 * @param progress how far from [from] to [to], read at layout time only.
 */
@Stable
class ColumnGeometry(val from: DaySpan, val to: DaySpan, val progress: () -> Float) {

    /** Every day either page shows, in order: the two overlap, so they run on unbroken. */
    val dates: List<LocalDate> = run {
        val first = minOf(from.start, to.start)
        val last = maxOf(from.end, to.end)
        (0..first.daysUntil(last)).map { first.plus(it, DateTimeUnit.DAY) }
    }

    fun slot(date: LocalDate, width: Float, gap: Float): ColumnSlot {
        val a = from.slot(date, width, gap)
        if (from == to) return a
        val b = to.slot(date, width, gap)
        val p = progress()
        return ColumnSlot(lerp(a.left, b.left, p), lerp(a.width, b.width, p))
    }

    /** The span of columns [range] covers, between its extent on each page. */
    fun extent(fromRange: ClosedRange<LocalDate>, toRange: ClosedRange<LocalDate>, width: Float, gap: Float): ColumnSlot {
        val a = from.extent(fromRange, width, gap)
        if (from == to && fromRange == toRange) return a
        val b = to.extent(toRange, width, gap)
        val p = progress()
        val left = lerp(a.left, b.left, p)
        return ColumnSlot(left, lerp(a.right, b.right, p) - left)
    }

    companion object {
        /** One page's columns, standing still. */
        fun of(span: DaySpan): ColumnGeometry = ColumnGeometry(span, span) { 0f }
    }
}

private fun DaySpan.extent(range: ClosedRange<LocalDate>, width: Float, gap: Float): ColumnSlot {
    val left = slot(range.start, width, gap).left
    return ColumnSlot(left, slot(range.endInclusive, width, gap).right - left)
}

internal fun lerp(a: Float, b: Float, p: Float): Float = a + (b - a) * p

/**
 * Lays [content] out once per day of [geometry], each across its column. The
 * columns are placed in the layout pass, so a view switch moving them every
 * frame re-lays them out without recomposing them. Children fill the height
 * the layout is given, or wrap it when it has none.
 */
@Composable
fun TimelineColumns(
    geometry: ColumnGeometry,
    gap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable (LocalDate) -> Unit,
) {
    val dates = geometry.dates
    Layout(
        content = {
            dates.forEach { date ->
                key(date) { Box(propagateMinConstraints = true) { content(date) } }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gapPx = gap.toPx()
        val slots = dates.map { geometry.slot(it, width.toFloat(), gapPx) }
        val placeables = measurables.mapIndexed { i, measurable ->
            val left = slots[i].left.roundToInt()
            val columnWidth = (slots[i].right.roundToInt() - left).coerceAtLeast(0)
            measurable.measure(constraints.copy(minWidth = columnWidth, maxWidth = columnWidth))
        }
        val height = placeables.maxOfOrNull { it.height }?.coerceAtLeast(constraints.minHeight)
            ?: constraints.minHeight
        layout(width, height) {
            placeables.forEachIndexed { i, placeable ->
                placeable.placeRelative(slots[i].left.roundToInt(), 0)
            }
        }
    }
}

/**
 * An all-day bar as each page lays it out: the days it covers there, clipped to
 * the page, and its lane. A bar only one page has keeps its days on the other,
 * so it slides out with them rather than vanishing.
 */
@Immutable
data class PlacedBar(
    val event: EventInstance,
    /** The day the bar is drawn from on the page it is tagged on, for the view morph. */
    val keyDate: LocalDate,
    val fromRange: ClosedRange<LocalDate>,
    val toRange: ClosedRange<LocalDate>,
    val fromLane: Int,
    val toLane: Int,
)

/** [state]'s all-day bars, standing still. */
internal fun staticBars(state: WeekUiState.Success): List<PlacedBar> =
    state.allDaySpans.map { span ->
        val range = state.days[span.startCol]..state.days[span.endCol]
        PlacedBar(span.event, range.start, range, range, span.lane, span.lane)
    }

/**
 * The all-day bars of both pages of a view switch, each once: matched by
 * occurrence, placed on each page as that page lays it out.
 */
internal fun morphBars(from: WeekUiState.Success?, to: WeekUiState.Success?): List<PlacedBar> {
    val fromBars = from?.let(::staticBars).orEmpty().associateBy { it.event.occurrence() }
    val toBars = to?.let(::staticBars).orEmpty().associateBy { it.event.occurrence() }
    return (fromBars.keys + toBars.keys).map { occurrence ->
        val a = fromBars[occurrence]
        val b = toBars[occurrence]
        PlacedBar(
            event = (a ?: b)!!.event,
            keyDate = (a ?: b)!!.keyDate,
            fromRange = (a ?: b)!!.fromRange,
            toRange = (b ?: a)!!.toRange,
            fromLane = (a ?: b)!!.fromLane,
            toLane = (b ?: a)!!.toLane,
        )
    }
}

private fun EventInstance.occurrence(): Pair<Long, Long> = eventId to start.toEpochMilliseconds()
