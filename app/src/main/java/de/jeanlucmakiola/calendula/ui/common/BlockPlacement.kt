package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.floret.identity.rememberReduceMotion

/** Gap a timed block leaves to its neighbours in the column. */
val BLOCK_OUTER_INSET = 1.dp

/** Padding between a timed block's edge and its text. */
val BLOCK_TEXT_PADDING = 4.dp

/** The same, above and below — what a block's height has to pay before any text. */
val BLOCK_TEXT_INSET = 2.dp

/** Most lines a time label may wrap over before it is worth more than a title line. */
const val MAX_TIME_LINES = 2

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
 * A week column is narrower than a "09:30–11:00" range, so the label takes a
 * second line rather than lose its end — but only out of a line the title
 * measured itself as not needing, never one it would have filled.
 */
@Composable
fun blockTimeLines(label: String, textWidth: Dp, spare: Dp): Int {
    val timeLineHeight = with(LocalDensity.current) {
        MaterialTheme.typography.labelSmall.lineHeight.toDp()
    }
    return if (spare >= timeLineHeight) {
        blockTextLines(
            text = label,
            // The style it is drawn in, or the budget measures a line the label
            // never uses (#219).
            style = MaterialTheme.typography.labelSmall.asEventTime(),
            textWidth = textWidth,
            max = MAX_TIME_LINES,
        )
    } else {
        1
    }
}

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
        style = MaterialTheme.typography.labelMedium
            .let { if (fontWeight == null) it else it.copy(fontWeight = fontWeight) },
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
    Crossfade(
        targetState = label,
        animationSpec = spec,
        label = "block-time",
        modifier = modifier,
    ) { text ->
        Text(
            text = text,
            // Regular weight against the title's medium above it (#219).
            style = MaterialTheme.typography.labelSmall.asEventTime(),
            maxLines = maxLines,
            overflow = overflow.overflow,
            softWrap = overflow.softWrap,
            color = color,
        )
    }
}

/** Where a timed block sits in its column, after tweening. */
@Immutable
data class BlockPlacement(val x: Dp, val y: Dp, val width: Dp, val height: Dp)

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
fun animatedBlockPlacement(x: Dp, y: Dp, width: Dp, height: Dp): BlockPlacement {
    val spec: FiniteAnimationSpec<Dp> = if (rememberReduceMotion() ||
        LocalTimelineZoom.current.isPinching
    ) {
        snap()
    } else {
        MaterialTheme.motionScheme.fastSpatialSpec()
    }
    val animatedX by animateDpAsState(x, spec, label = "block-x")
    val animatedY by animateDpAsState(y, spec, label = "block-y")
    val animatedWidth by animateDpAsState(width, spec, label = "block-width")
    val animatedHeight by animateDpAsState(height, spec, label = "block-height")
    return BlockPlacement(animatedX, animatedY, animatedWidth, animatedHeight)
}
