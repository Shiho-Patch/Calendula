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

/** Stroke an event that is not yours to be at yet is outlined with. */
val EVENT_OUTLINE_WIDTH = 1.dp

/**
 * How one event's chip or block is painted, which turns on your answer to its
 * invitation (#230). An event you are going to keeps the solid fill; one still
 * waiting on your answer, and one you refused, are drawn hollow with their
 * calendar's colour as an outline, so a glance at the grid separates "mine" from
 * "asked of me". Declined additionally keeps its strike-through (#180).
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
    if (event.response == EventResponse.Going) {
        val fill = eventFill(event.color, dark, soften)
        return EventPaint(
            fill = fill,
            outline = null,
            titleInk = eventInk(fill, alpha = TITLE_INK_ALPHA),
            secondaryInk = eventInk(fill, alpha = SECONDARY_INK_ALPHA),
            decoration = null,
        )
    }
    return EventPaint(
        fill = hollowFill,
        outline = eventAccent(event.color, dark, soften),
        // Ink off the page rather than off the chip: there is no container
        // behind the text any more to pin a contrast against.
        titleInk = MaterialTheme.colorScheme.onSurface,
        secondaryInk = MaterialTheme.colorScheme.onSurfaceVariant,
        decoration = declinedDecoration(event.response == EventResponse.Declined),
    )
}

/** Seats a chip or block on [shape]: its fill, plus the outline when it has one. */
fun Modifier.eventSurface(paint: EventPaint, shape: Shape): Modifier =
    background(paint.fill, shape)
        .then(
            paint.outline?.let { Modifier.border(EVENT_OUTLINE_WIDTH, it, shape) } ?: Modifier,
        )
