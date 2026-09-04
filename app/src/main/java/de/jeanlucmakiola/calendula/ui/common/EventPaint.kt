package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.EventResponse
import de.jeanlucmakiola.calendula.domain.isDeclined

/** Distance between two stripes of the weave an unanswered invitation wears. */
private val HATCH_PITCH = 7.dp

/** How thick each of those stripes is drawn. */
private val HATCH_STROKE = 2.5.dp

/**
 * How far the weave lifts off the container under it. Held low: it has to be
 * legible on a 16dp month chip without competing with the title over it.
 */
private const val HATCH_ALPHA = 0.22f

/**
 * How one event's chip or block is painted, which turns on your answer to its
 * invitation (#180, #230).
 *
 * Every state keeps the solid container — at month-chip size the fill *is* what
 * identifies the event, so taking it away costs more than any mark it could
 * make room for. An invitation you have not answered is instead woven over with
 * a diagonal hatch, the long-standing calendar mark for "not settled yet", which
 * survives both the past-event dim and a chip only a few dp tall. A declined one
 * keeps its strike-through.
 */
@Immutable
data class EventPaint(
    val fill: Color,
    val titleInk: Color,
    val secondaryInk: Color,
    val decoration: TextDecoration?,
    /** Stripe colour for an unanswered invitation; null when the chip is plain. */
    val hatch: Color?,
)

/** [EventPaint] for [event] on a [dark] scheme. */
@Composable
fun eventPaint(event: EventInstance, dark: Boolean): EventPaint {
    val soften = LocalSoftenColors.current
    val fill = eventFill(event.color, dark, soften)
    return EventPaint(
        fill = fill,
        titleInk = eventInk(fill, alpha = TITLE_INK_ALPHA),
        secondaryInk = eventInk(fill, alpha = SECONDARY_INK_ALPHA),
        decoration = declinedDecoration(event.isDeclined),
        // Off the ink rather than the raw colour, so the weave reads on a pale
        // container and on a deep one alike.
        hatch = if (event.response == EventResponse.Invited) {
            eventInk(fill, alpha = HATCH_ALPHA)
        } else {
            null
        },
    )
}

/** Seats a chip or block on [shape]: its fill, plus the weave when it has one. */
fun Modifier.eventSurface(paint: EventPaint, shape: Shape): Modifier {
    val filled = background(paint.fill, shape)
    val hatch = paint.hatch ?: return filled
    // Clipped rather than drawn per corner: the stripes run past every edge, and
    // a bar cut at a week boundary has square corners on that side anyway.
    return filled.clip(shape).drawBehind { drawHatch(hatch) }
}

/** Parallel 45° stripes across the whole box, running up towards the trailing edge. */
private fun DrawScope.drawHatch(color: Color) {
    val pitch = HATCH_PITCH.toPx()
    val stroke = HATCH_STROKE.toPx()
    val height = size.height
    // Start a full height's worth to the left, so the first stripes to reach the
    // box's leading edge are already on screen.
    var x = -height
    while (x < size.width) {
        drawLine(
            color = color,
            start = Offset(x, height),
            end = Offset(x + height, 0f),
            strokeWidth = stroke,
            cap = StrokeCap.Square,
        )
        x += pitch
    }
}
