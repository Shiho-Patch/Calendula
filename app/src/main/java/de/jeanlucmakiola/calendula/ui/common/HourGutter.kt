package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.floret.locale.currentLocale

/** Width of the hour gutter down the start edge of the day and week timelines. */
val GUTTER_WIDTH = 48.dp

/**
 * Start inset for the gutter's content (week badge + hour labels) so it centres
 * on the top bar's hamburger: with a 48dp gutter, 8dp lands the centre at 28dp,
 * matching the icon button's centre.
 */
val GUTTER_CONTENT_START_INSET = 8.dp

/**
 * End inset for everything that lines up with the day columns — the week's day
 * header, the all-day strip and the timeline itself. Without it the last column
 * runs flush into the screen edge while the gutter gives the other side room
 * (#192); 8dp mirrors [GUTTER_CONTENT_START_INSET].
 */
val TIMELINE_CONTENT_END_INSET = 8.dp

private val BADGE_HEIGHT = 20.dp

/** How far the fixed hour labels recede while a block is being dragged. */
private const val DIMMED_HOUR_ALPHA = 0.3f

/**
 * The timeline's hour gutter. Scrolls in sync with the day columns through the
 * shared [scrollState], and while [dragController] holds a lifted block it dims
 * the hour labels and floats a badge with the drag's current start time at the
 * row the block would land on.
 */
@Composable
fun HourGutter(
    scrollState: ScrollState,
    hourHeight: Dp,
    dragController: TimelineDragController,
    modifier: Modifier = Modifier,
) {
    val use24Hour = LocalUse24HourFormat.current
    val locale = currentLocale()
    // Derived: the drag is rewritten every frame, its snapped start once a slot.
    val dragStartMin by remember(dragController) {
        derivedStateOf { dragController.drag?.startMin }
    }
    val hourAlpha by animateFloatAsState(
        targetValue = if (dragStartMin != null) DIMMED_HOUR_ALPHA else 1f,
        label = "hourLabelAlpha",
    )
    // Each label straddles the boundary it names, so it lines up with the seam
    // the hour grid leaves there instead of hanging below it. Derived from the
    // label's own line height, so it holds at any font scale.
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelLift = with(LocalDensity.current) { labelStyle.lineHeight.toDp() } / 2

    Box(
        modifier = modifier
            .width(GUTTER_WIDTH)
            .padding(start = GUTTER_CONTENT_START_INSET)
            .fillMaxHeight()
            .verticalScroll(scrollState),
    ) {
        Column {
            (0 until 24).forEach { h ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(hourHeight),
                ) {
                    if (h > 0) {
                        Text(
                            text = formatHourLabel(h, use24Hour, locale),
                            style = labelStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                .copy(alpha = hourAlpha),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = -labelLift),
                        )
                    }
                }
            }
        }
        dragStartMin?.let { startMin ->
            val top = (hourHeight * (startMin / 60f) - BADGE_HEIGHT / 2).coerceAtLeast(0.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = top)
                    .height(BADGE_HEIGHT)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(horizontal = 4.dp)
                    // The dragged block already announces this time.
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatGutterTime(startMin, use24Hour, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}
