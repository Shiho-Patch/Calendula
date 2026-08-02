package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import de.jeanlucmakiola.floret.identity.rememberReduceMotion

/** Where a timed block sits in its column, after tweening. */
@Immutable
data class BlockPlacement(val x: Dp, val y: Dp, val width: Dp, val height: Dp)

/**
 * A timed block's placement, tweened rather than jumped. Every bound a block
 * has changes for a reason the user just caused — a drop landing at a new time,
 * an undo putting it back, a neighbour arriving and halving both lanes — and all
 * of them read better as motion than as a new layout appearing.
 *
 * Continuity comes from the caller keying each block by identity; a block
 * composed for the first time starts *at* its target, so nothing flies in from
 * the corner on the first frame.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun animatedBlockPlacement(x: Dp, y: Dp, width: Dp, height: Dp): BlockPlacement {
    val spec: FiniteAnimationSpec<Dp> = if (rememberReduceMotion()) {
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
