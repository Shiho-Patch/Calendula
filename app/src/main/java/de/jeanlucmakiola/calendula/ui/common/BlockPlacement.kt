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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import de.jeanlucmakiola.floret.identity.rememberReduceMotion

/**
 * A timed block's own time label, crossfaded rather than replaced — the block
 * slides to its new slot, so the label shouldn't change in a single frame.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BlockTimeLabel(label: String, color: Color, modifier: Modifier = Modifier) {
    val spec: FiniteAnimationSpec<Float> = if (rememberReduceMotion()) {
        snap()
    } else {
        MaterialTheme.motionScheme.fastEffectsSpec()
    }
    Crossfade(
        targetState = label,
        animationSpec = spec,
        label = "block-time",
        modifier = modifier,
    ) { text ->
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
