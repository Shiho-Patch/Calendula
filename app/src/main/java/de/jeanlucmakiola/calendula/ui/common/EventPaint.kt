package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.EventResponse
import de.jeanlucmakiola.calendula.domain.isDeclined

/** Stroke an invitation you have not answered is outlined with. */
val EVENT_OUTLINE_WIDTH = 1.dp

/**
 * How one event's chip or block is painted, which turns on your answer to its
 * invitation (#180, #230).
 *
 * An invitation you have not answered is drawn as an outline: its calendar's
 * colour on the border and on the text, over the same container the day cells
 * and timeline columns are themselves painted in. So it holds a chip's shape and
 * a chip's weight without claiming the filled container an event you are going
 * to gets. A declined one keeps the fill and its strike-through.
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
        // eventAccent is the identity pinned to contrast with an ordinary
        // surface rather than with ink, which is exactly what border and text
        // both need here.
        val accent = eventAccent(event.color, dark, soften)
        return EventPaint(
            fill = MaterialTheme.colorScheme.surfaceContainer,
            outline = accent,
            titleInk = accent,
            secondaryInk = accent.copy(alpha = SECONDARY_INK_ALPHA),
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

/** Seats a chip or block on [shape]: its fill, plus the border when it has one. */
fun Modifier.eventSurface(paint: EventPaint, shape: Shape): Modifier =
    background(paint.fill, shape)
        .then(
            paint.outline?.let { Modifier.border(EVENT_OUTLINE_WIDTH, it, shape) } ?: Modifier,
        )
