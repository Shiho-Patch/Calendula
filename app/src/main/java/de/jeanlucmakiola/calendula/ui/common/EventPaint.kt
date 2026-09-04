package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.EventResponse
import de.jeanlucmakiola.calendula.domain.isDeclined

/** Stroke an invitation you have not answered is outlined with. */
val EVENT_OUTLINE_WIDTH = 1.dp

/**
 * How one event's chip or block is painted, which turns on your answer to its
 * invitation (#230). An invitation you have not answered is drawn hollow — its
 * calendar's colour as an outline, nothing filled in — so a glance at the grid
 * separates "mine" from "still asked of me". Everything you have answered keeps
 * the solid fill, declined included: its strike-through needs a container under
 * it to read at chip size.
 */
@Immutable
data class EventPaint(
    val fill: Color,
    /** Null for a solid chip; the outline colour when the chip is hollow. */
    val outline: Color?,
    val titleInk: Color,
    val secondaryInk: Color,
    val decoration: TextDecoration?,
)

/**
 * [EventPaint] for [event]. [hollowFill] is what an outlined chip sits on — the
 * grids leave it transparent so the day cell shows through, while a floating
 * drag copy passes an opaque surface so it keeps a card's body under its shadow.
 */
@Composable
fun eventPaint(
    event: EventInstance,
    dark: Boolean,
    hollowFill: Color = Color.Transparent,
): EventPaint {
    val soften = LocalSoftenColors.current
    if (event.response == EventResponse.Invited) {
        return EventPaint(
            fill = hollowFill,
            outline = eventAccent(event.color, dark, soften),
            // Ink off the page rather than off the chip: there is no container
            // behind the text to pin a contrast against.
            titleInk = MaterialTheme.colorScheme.onSurface,
            secondaryInk = MaterialTheme.colorScheme.onSurfaceVariant,
            decoration = null,
        )
    }
    val fill = eventFill(event.color, dark, soften)
    return EventPaint(
        fill = fill,
        outline = null,
        titleInk = eventInk(fill, alpha = TITLE_INK_ALPHA),
        secondaryInk = eventInk(fill, alpha = SECONDARY_INK_ALPHA),
        decoration = declinedDecoration(event.isDeclined),
    )
}

/** Seats a chip or block on [shape]: its fill, plus the outline when it has one. */
fun Modifier.eventSurface(paint: EventPaint, shape: Shape): Modifier =
    background(paint.fill, shape)
        .then(
            paint.outline?.let { Modifier.border(EVENT_OUTLINE_WIDTH, it, shape) } ?: Modifier,
        )
