package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.floret.identity.rememberReduceMotion
import kotlin.math.roundToInt

/** Gap a timed block leaves to its neighbours in the column. */
val BLOCK_OUTER_INSET = 1.dp

/** Padding between a timed block's edge and its text. */
val BLOCK_TEXT_PADDING = 4.dp

/** The same, above and below — what a block's height has to pay before any text. */
val BLOCK_TEXT_INSET = 2.dp

/**
 * What a timed block of a given height has to spend on text, and what each line
 * of it costs.
 */
@Immutable
data class BlockTextMetrics(
    /** Padding above and below the text — see [rememberBlockTextMetrics]. */
    val inset: Dp,
    /** Height left for text once [inset] is paid at both edges. */
    val available: Dp,
    /** What the first line of a title draws in. */
    val titleLine: Dp,
    /** What every title line after the first adds. */
    val titleLeading: Dp,
    /** What the time label's one line draws in. */
    val timeLine: Dp,
) {
    /** Whether the block can draw a title at all. */
    val fitsTitle: Boolean get() = available >= titleLine

    /** Height a title of [lines] lines occupies. */
    fun titleHeight(lines: Int): Dp =
        if (lines <= 0) 0.dp else titleLine + titleLeading * (lines - 1)

    /** Title lines that fit [within], which may be none. */
    fun titleBudget(within: Dp): Int =
        if (within < titleLine) 0 else 1 + ((within - titleLine) / titleLeading).toInt()
}

/**
 * The vertical padding a block [height] tall can afford around a title line of
 * [titleLine].
 *
 * The inset is what the block gives up first: breathing room is worth having
 * where there is room to breathe, but on a block down to its last few pixels a
 * bare colour chip where a label would have fit reads as a rendering fault. It
 * tapers rather than snapping, so a pinch closes the gap gradually instead of
 * dropping it in one frame (#289).
 */
internal fun blockTextInset(height: Dp, titleLine: Dp): Dp =
    minOf(BLOCK_TEXT_INSET, (height - titleLine) / 2).coerceAtLeast(0.dp)

/** Text metrics for a timed block [height] tall. */
@Composable
fun rememberBlockTextMetrics(height: Dp): BlockTextMetrics {
    val titleStyle = MaterialTheme.typography.labelMedium
    val titleLine = rememberTrimmedLineHeight(titleStyle)
    // Packed, so a second line costs what the first did rather than a whole
    // Material line box — the gap between two lines of a wrapped title is the
    // one place a block pays that leading twice (#190).
    val titleLeading = titleLine
    val timeLine = rememberTrimmedLineHeight(MaterialTheme.typography.labelSmall.asEventTime())
    val inset = blockTextInset(height, titleLine)
    return BlockTextMetrics(
        inset = inset,
        available = height - inset * 2,
        titleLine = titleLine,
        titleLeading = titleLeading,
        timeLine = timeLine,
    )
}

/**
 * Lines [text] needs to render whole at [textWidth], capped at [max].
 *
 * Lets a block hand out its height by what the text actually asks for rather
 * than by what would fit: a title that wants one line should not be given three
 * that the time label could have used, and a week column is narrower than a
 * "09:30–11:00" range so the range should not be assumed to want one.
 */
@Composable
fun blockTextLines(text: String, style: TextStyle, textWidth: Dp, max: Int): Int {
    val measurer = rememberTextMeasurer()
    val widthPx = with(LocalDensity.current) { textWidth.roundToPx() }
    return remember(text, style, widthPx, max, measurer) {
        if (max <= 1 || widthPx <= 0) {
            1
        } else {
            measurer.measure(
                text = text,
                style = style,
                constraints = Constraints(maxWidth = widthPx),
            ).lineCount.coerceIn(1, max)
        }
    }
}

/**
 * Lines the time label may take at [textWidth], out of the [spare] height left
 * once the title and the label's own first line are paid for.
 *
 * A week column is narrower than a "09:30–11:00" range — and a 12-hour
 * "6:00 PM–7:00 PM" can want four lines — so the label wraps over as many as it
 * needs rather than lose its end, but only out of lines the title measured
 * itself as not needing, never one it would have filled.
 */
@Composable
fun blockTimeLines(label: String, textWidth: Dp, spare: Dp): Int {
    val timeLineHeight = rememberTrimmedLineHeight(
        MaterialTheme.typography.labelSmall.asEventTime(),
    )
    return blockTextLines(
        text = label,
        // The style it is drawn in, or the budget measures a line the label
        // never uses (#219).
        style = MaterialTheme.typography.labelSmall.asEventTime(),
        textWidth = textWidth,
        max = timeLinesWithin(spare, timeLineHeight),
    )
}

/** Lines a time label may take: its own first one, plus every whole [timeLine] in [spare]. */
internal fun timeLinesWithin(spare: Dp, timeLine: Dp): Int =
    if (spare < timeLine || timeLine <= 0.dp) 1 else 1 + (spare / timeLine).toInt()

/**
 * The time range as a timed block draws it.
 *
 * Each time stays whole where the column is wide enough for one, so a 12-hour
 * label never parts "6:00" from its "PM": the range breaks at the dash, which
 * rides on whichever side has room for it. Where not even one time fits, the
 * text wraps wherever it can.
 */
@Composable
fun rememberBlockTimeRange(start: String, end: String, textWidth: Dp): String {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.asEventTime()
    val widthPx = with(LocalDensity.current) { textWidth.roundToPx() }
    return remember(start, end, style, widthPx, measurer) {
        blockTimeRange(start, end) {
            measurer.measure(text = it, style = style, softWrap = false).size.width <= widthPx
        }
    }
}

internal fun blockTimeRange(start: String, end: String, fits: (String) -> Boolean): String {
    val from = start.replace(' ', NO_BREAK_SPACE)
    val to = end.replace(' ', NO_BREAK_SPACE)
    return when {
        fits("$from–$to") -> "$from–$to"
        !fits(from) || !fits(to) -> "$start–$ZERO_WIDTH_SPACE$end"
        fits("$from–") -> "$from–\n$to"
        fits("–$to") -> "$from\n–$to"
        else -> "$from\n$to"
    }
}

private const val NO_BREAK_SPACE = ' '
private const val ZERO_WIDTH_SPACE = '​'

/**
 * A timed block's title, over at most [maxLines], breaking at word boundaries.
 *
 * #164 clipped the last line mid-glyph so none of a narrow chip's few
 * characters went on an ellipsis. On a block that can wrap, whole words read
 * better than full lines do: "Farmers Market" over two lines beats "Farmer" /
 * "s Marke". A single line still clips, having nowhere to wrap to.
 */
@Composable
fun BlockTitle(
    title: String,
    maxLines: Int,
    color: Color,
    modifier: Modifier = Modifier,
    textDecoration: TextDecoration? = null,
    fontWeight: FontWeight? = null,
) {
    val overflow = eventTitleOverflow(singleLine = maxLines == 1)
    Text(
        text = title,
        modifier = modifier,
        style = rememberPackedLines(
            MaterialTheme.typography.labelMedium
                .let { if (fontWeight == null) it else it.copy(fontWeight = fontWeight) },
        ),
        maxLines = maxLines,
        overflow = overflow.overflow,
        softWrap = overflow.softWrap,
        color = color,
        textDecoration = textDecoration,
    )
}

/**
 * A timed block's own time label, crossfaded rather than replaced — the block
 * slides to its new slot, so the label shouldn't change in a single frame.
 *
 * Overflows like a title does (#164): the "…" costs two characters of a string
 * that is nothing but characters, so the label clips at the block's edge
 * instead. [maxLines] lets a narrow column spend spare height on the range
 * rather than losing its end.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BlockTimeLabel(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    val spec: FiniteAnimationSpec<Float> = if (rememberReduceMotion()) {
        snap()
    } else {
        MaterialTheme.motionScheme.fastEffectsSpec()
    }
    val overflow = eventTitleOverflow(singleLine = maxLines == 1)
    // Regular weight against the title's medium above it (#219).
    val style = rememberPackedLines(MaterialTheme.typography.labelSmall.asEventTime())
    Crossfade(
        targetState = label,
        animationSpec = spec,
        label = "block-time",
        modifier = modifier,
    ) { text ->
        Text(
            text = text,
            style = style,
            maxLines = maxLines,
            overflow = overflow.overflow,
            softWrap = overflow.softWrap,
            color = color,
        )
    }
}

/**
 * Where a timed block sits in its column, after tweening: across as shares of
 * the column's width, so a column resizing carries its blocks with it (#184),
 * down in the timeline's own units.
 */
@Immutable
data class BlockPlacement(val xFraction: Float, val widthFraction: Float, val y: Dp, val height: Dp)

/**
 * A timed block's placement, tweened rather than jumped. Continuity comes from
 * the caller keying each block by identity; a block composed for the first time
 * starts at its target, so nothing flies in on the first frame.
 *
 * A pinch-zoom rewrites the hour height every pointer frame, and the gutter and
 * grid lines follow it instantly — so the tween stands down for the gesture
 * rather than leaving the blocks trailing the ruler they are measured against.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun animatedBlockPlacement(xFraction: Float, widthFraction: Float, y: Dp, height: Dp): BlockPlacement {
    val still = rememberReduceMotion() || LocalTimelineZoom.current.isPinching
    val dpSpec: FiniteAnimationSpec<Dp> = if (still) snap() else MaterialTheme.motionScheme.fastSpatialSpec()
    val shareSpec: FiniteAnimationSpec<Float> = if (still) snap() else MaterialTheme.motionScheme.fastSpatialSpec()
    val animatedX by animateFloatAsState(xFraction, shareSpec, label = "block-x")
    val animatedWidth by animateFloatAsState(widthFraction, shareSpec, label = "block-width")
    val animatedY by animateDpAsState(y, dpSpec, label = "block-y")
    val animatedHeight by animateDpAsState(height, dpSpec, label = "block-height")
    return BlockPlacement(animatedX, animatedWidth, animatedY, animatedHeight)
}

/** Seats a block across its [placement]'s share of a [LaneColumn]. */
fun Modifier.laneSlot(placement: BlockPlacement): Modifier =
    then(LaneSlot(placement.xFraction, placement.widthFraction))

private data class LaneSlot(val x: Float, val width: Float) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@LaneSlot
}

/**
 * A day column's blocks, each measured across the share of the column its
 * [laneSlot] gives it; anything else fills the column. The shares are applied
 * at layout time, so the column can change width every frame without its
 * blocks recomposing.
 */
@Composable
fun LaneColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placed = measurables.map { measurable ->
            val slot = measurable.parentData as? LaneSlot
            if (slot == null) {
                measurable.measure(loose) to 0
            } else {
                val left = (slot.x * width).roundToInt()
                val right = ((slot.x + slot.width) * width).roundToInt()
                val laneWidth = (right - left).coerceAtLeast(0)
                measurable.measure(loose.copy(minWidth = laneWidth, maxWidth = laneWidth)) to left
            }
        }
        layout(width, constraints.maxHeight) {
            placed.forEach { (placeable, x) -> placeable.placeRelative(x, 0) }
        }
    }
}
