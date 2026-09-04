package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.EventResponse
import de.jeanlucmakiola.calendula.domain.isDeclined

/** Stroke an invitation you have not answered is outlined with. */
val EVENT_OUTLINE_WIDTH = 1.dp

/**
 * The surface an event chip is drawn on, which an outlined one fills itself with
 * so it reads as the background showing through rather than as a pale block laid
 * over it. Defaults to the `surfaceContainer` the month cells and the timeline
 * columns carry; the all-day strips sit on plain `surface` and a floating drag
 * copy wants a lifted one, so both provide their own.
 */
val LocalChipGround = compositionLocalOf { Color.Unspecified }

/**
 * Which edges of a chip the event runs past. Those edges are squared by
 * [monthBarShape] / [timedBlockShape] so the cut reads as "this carries on", and
 * an outline has to leave them open for the same reason — a stroke all the way
 * round would close a bar carried across a week boundary into two boxes.
 */
@Immutable
data class ChipCuts(
    val start: Boolean = false,
    val end: Boolean = false,
    val top: Boolean = false,
    val bottom: Boolean = false,
)

/** [ChipCuts] for a month or all-day bar, which is only ever cut left and right. */
fun monthBarCuts(continuesLeft: Boolean, continuesRight: Boolean): ChipCuts =
    ChipCuts(start = continuesLeft, end = continuesRight)

/** [ChipCuts] for a timed block, which is only ever cut top and bottom. */
fun timedBlockCuts(continuesBefore: Boolean, continuesAfter: Boolean): ChipCuts =
    ChipCuts(top = continuesBefore, bottom = continuesAfter)

/**
 * How one event's chip or block is painted, which turns on your answer to its
 * invitation (#180, #230).
 *
 * An invitation you have not answered is drawn as an outline: its calendar's
 * colour on the border and on the title, over the surface the chip sits on. So
 * it holds a chip's shape and a chip's weight without claiming the filled
 * container an event you are going to gets. A declined one keeps the fill and
 * its strike-through.
 */
@Immutable
data class EventPaint(
    val fill: Color,
    /** Null for a filled chip; the border colour when the chip is outlined. */
    val outline: Color?,
    val titleInk: Color,
    val secondaryInk: Color,
    val decoration: TextDecoration?,
    /**
     * Weight to set the title at, or null to keep whatever the surface's own
     * text style carries.
     */
    val titleWeight: FontWeight?,
)

/** [EventPaint] for [event] on a [dark] scheme. */
@Composable
fun eventPaint(event: EventInstance, dark: Boolean): EventPaint {
    val soften = LocalSoftenColors.current
    if (event.response == EventResponse.Invited) {
        // Harmonised even when the setting is off. Raw mode exists so a filled
        // container matches what the sync source paints, and an outlined chip
        // has no container — what it has is coloured *text*, which needs the
        // lightness pinned against the surface or a pale calendar goes
        // unreadable (eventTone returns the raw colour verbatim otherwise).
        val accent = eventAccent(event.color, dark, soften = true)
        val ground = LocalChipGround.current.takeIf { it != Color.Unspecified }
            ?: MaterialTheme.colorScheme.surfaceContainer
        return EventPaint(
            fill = ground,
            outline = accent,
            titleInk = accent,
            // A neutral token rather than the accent faded: the time is the
            // smallest text on the chip, and an alpha step off an accent that is
            // itself only just clear of the surface is where legibility goes.
            secondaryInk = MaterialTheme.colorScheme.onSurfaceVariant,
            decoration = null,
            // The label styles' medium weight is set to carry ink on a filled
            // container. In a calendar colour on a plain one it thickens into
            // something harder to read, so step it back to regular.
            titleWeight = FontWeight.Normal,
        )
    }
    val fill = eventFill(event.color, dark, soften)
    return EventPaint(
        fill = fill,
        outline = null,
        titleInk = eventInk(fill, alpha = TITLE_INK_ALPHA),
        secondaryInk = eventInk(fill, alpha = SECONDARY_INK_ALPHA),
        decoration = declinedDecoration(event.isDeclined),
        titleWeight = null,
    )
}

/** [this] set at the weight [paint] asks a title for, for measuring and for drawing alike. */
fun TextStyle.withTitleWeight(paint: EventPaint): TextStyle =
    if (paint.titleWeight == null) this else copy(fontWeight = paint.titleWeight)

/** Seats a chip or block on [shape]: its fill, plus the border when it has one. */
fun Modifier.eventSurface(
    paint: EventPaint,
    shape: Shape,
    cuts: ChipCuts = ChipCuts(),
): Modifier {
    val filled = background(paint.fill, shape)
    val outline = paint.outline ?: return filled
    return filled.clip(shape).drawBehind { drawChipOutline(outline, cuts) }
}

/**
 * The border, stroked as one round-rect whose edges run past the chip on every
 * side the event continues over. Clipped to the chip's own shape, so those
 * strokes — and the corners that would have turned back in — fall outside and
 * the edge stays open.
 */
private fun DrawScope.drawChipOutline(color: Color, cuts: ChipCuts) {
    val stroke = EVENT_OUTLINE_WIDTH.toPx()
    val radius = EVENT_CHIP_CORNER.toPx()
    // Far enough out that the corner arc clears the clip too, not just the edge.
    val bleed = radius + stroke
    val rtl = layoutDirection == LayoutDirection.Rtl
    val leftCut = if (rtl) cuts.end else cuts.start
    val rightCut = if (rtl) cuts.start else cuts.end
    val left = if (leftCut) -bleed else stroke / 2f
    val top = if (cuts.top) -bleed else stroke / 2f
    val right = if (rightCut) size.width + bleed else size.width - stroke / 2f
    val bottom = if (cuts.bottom) size.height + bleed else size.height - stroke / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(right - left, bottom - top),
        cornerRadius = CornerRadius(radius),
        style = Stroke(width = stroke),
    )
}
