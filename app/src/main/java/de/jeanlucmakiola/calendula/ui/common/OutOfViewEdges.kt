package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.color.edgeHintTone
import de.jeanlucmakiola.calendula.ui.week.TimedBlock
import de.jeanlucmakiola.floret.identity.rememberReduceMotion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Thickness of the out-of-view edge bar. */
private val EDGE_BAR_THICKNESS = 3.dp

/** Tappable band the bar sits in, taller than the bar itself. */
private val EDGE_TOUCH_HEIGHT = 24.dp

/**
 * How far the bar sits in from the viewport's edges — enough to clear the
 * timeline's rounded 16dp corners on the outer columns.
 */
private val EDGE_BAR_VERTICAL_INSET = 4.dp
private val EDGE_BAR_HORIZONTAL_INSET = 6.dp

/** A block showing no more than this at the edge still counts as out of view. */
private val EDGE_SLACK = 12.dp

/** The nearest timed blocks wholly above and below a column's viewport. */
@Immutable
data class OutOfViewEvents(val above: TimedBlock?, val below: TimedBlock?) {
    companion object {
        val None = OutOfViewEvents(null, null)
    }
}

/** Which edge of the viewport a hidden event sits past. */
enum class ViewportEdge { Top, Bottom }

/** Where [block]'s rendered bottom lands, in minutes — short blocks keep a floor. */
internal fun renderedEndMin(block: TimedBlock): Float =
    maxOf(block.endMin.toFloat(), block.startMin + MIN_EVENT_FRACTION * 60f)

/**
 * The blocks of one column lying out of a viewport spanning [topMin]..[bottomMin]
 * (minutes of the day), nearest first. A block that shows at most [slackMin] at
 * the edge counts as out of view, but only if some of it is actually hidden, so
 * a column scrolled to its very top or bottom never points past it.
 */
fun outOfViewEvents(
    blocks: List<TimedBlock>,
    topMin: Float,
    bottomMin: Float,
    slackMin: Float,
): OutOfViewEvents {
    val above = blocks
        .filter { it.startMin < topMin && renderedEndMin(it) <= topMin + slackMin }
        .maxWithOrNull(compareBy<TimedBlock>({ renderedEndMin(it) }, { it.startMin }))
    val below = blocks
        .filter { renderedEndMin(it) > bottomMin && it.startMin >= bottomMin - slackMin }
        .minWithOrNull(compareBy<TimedBlock>({ it.startMin }, { -renderedEndMin(it) }))
    return OutOfViewEvents(above, below)
}

/**
 * The scroll offset that brings [block] into a [viewportPx]-tall viewport from
 * past [edge], with [marginPx] of room so it clears the edge bar. An event above
 * lands with its start at the top; one below lands whole at the bottom when it
 * fits, and with its start at the top when it does not.
 */
fun revealScrollTarget(
    block: TimedBlock,
    edge: ViewportEdge,
    hourPx: Float,
    viewportPx: Int,
    marginPx: Float,
    maxScroll: Int,
): Int {
    val startPx = block.startMin / 60f * hourPx
    val endPx = renderedEndMin(block) / 60f * hourPx
    val target = when (edge) {
        ViewportEdge.Top -> startPx - marginPx
        ViewportEdge.Bottom -> minOf(endPx + marginPx - viewportPx, startPx - marginPx)
    }
    return target.roundToInt().coerceIn(0, maxOf(maxScroll, 0))
}

/**
 * Thin bars at the top and bottom of a day column's visible viewport, tinted in
 * the colour of the nearest timed event hidden past that edge (#188). Tapping
 * one scrolls the event into view.
 *
 * Lives inside the column's scrolling content, so taps on it still let the
 * timeline scroll and pinch, and follows the viewport by offsetting against
 * [scrollState] at placement rather than recomposing every frame.
 */
@Composable
fun BoxScope.OutOfViewEdgeBars(
    blocks: List<TimedBlock>,
    scrollState: ScrollState,
    hourPx: Float,
    dark: Boolean,
) {
    val density = LocalDensity.current
    val slackPx = with(density) { EDGE_SLACK.toPx() }
    val touchPx = with(density) { EDGE_TOUCH_HEIGHT.roundToPx() }
    val events by remember(blocks, hourPx, slackPx, scrollState) {
        derivedStateOf {
            val viewport = scrollState.viewportSize
            if (viewport <= 0 || hourPx <= 0f) {
                OutOfViewEvents.None
            } else {
                val top = scrollState.value.toFloat()
                outOfViewEvents(
                    blocks = blocks,
                    topMin = top / hourPx * 60f,
                    bottomMin = (top + viewport) / hourPx * 60f,
                    slackMin = slackPx / hourPx * 60f,
                )
            }
        }
    }
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val revealSpec = rememberCalendarPageSpec()
    val reveal: (TimedBlock, ViewportEdge) -> Unit = { block, edge ->
        val target = revealScrollTarget(
            block = block,
            edge = edge,
            hourPx = hourPx,
            viewportPx = scrollState.viewportSize,
            marginPx = touchPx.toFloat(),
            maxScroll = scrollState.maxValue,
        )
        scope.launch {
            if (reduceMotion) {
                scrollState.scrollTo(target)
            } else {
                scrollState.animateScrollTo(target, revealSpec)
            }
        }
    }
    EdgeBar(
        block = events.above,
        edge = ViewportEdge.Top,
        dark = dark,
        onReveal = reveal,
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset(0, scrollState.value) },
    )
    EdgeBar(
        block = events.below,
        edge = ViewportEdge.Bottom,
        dark = dark,
        onReveal = reveal,
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset { IntOffset(0, scrollState.value + scrollState.viewportSize - touchPx) },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EdgeBar(
    block: TimedBlock?,
    edge: ViewportEdge,
    dark: Boolean,
    onReveal: (TimedBlock, ViewportEdge) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Held so the bar keeps its colour, and its tap, while it fades out.
    val held = remember { mutableStateOf<TimedBlock?>(null) }
    SideEffect { if (block != null) held.value = block }
    val shown = block ?: held.value
    val soften = LocalSoftenColors.current
    val target = shown?.let { Color(edgeHintTone(it.event.color, dark, soften)) } ?: Color.Transparent
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "edge-bar-color",
    )
    val label = stringResource(
        if (edge == ViewportEdge.Top) R.string.timeline_earlier_events else R.string.timeline_later_events,
    )
    AnimatedVisibility(
        visible = block != null,
        enter = fadeIn(effects),
        exit = fadeOut(effects),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(EDGE_TOUCH_HEIGHT)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    // The scroll it sets off is the feedback; a ripple across the
                    // column's edge would be the noise the bar is meant to avoid.
                    indication = null,
                    role = Role.Button,
                ) { shown?.let { onReveal(it, edge) } }
                .semantics { contentDescription = label },
            contentAlignment = if (edge == ViewportEdge.Top) Alignment.TopCenter else Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = EDGE_BAR_HORIZONTAL_INSET,
                        vertical = EDGE_BAR_VERTICAL_INSET,
                    )
                    .height(EDGE_BAR_THICKNESS)
                    .background(color, CircleShape),
            )
        }
    }
}
