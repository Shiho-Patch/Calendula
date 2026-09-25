package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.floor

/**
 * One height for every page of something that varies from page to page — the
 * timelines' all-day strip, the split grid's rows — blended between the two
 * pages a swipe sits between, so what sits below it moves with the finger rather
 * than jumping on settle. It follows the swipe directly and only springs when a
 * page's own height changes at rest. [fallback] stands in until the pages have
 * reported theirs, so it doesn't open from zero.
 *
 * @param heights each composed page's own height, by page index.
 */
@Composable
fun pagedHeight(pagerState: PagerState, heights: Map<Int, Dp>, fallback: Dp): Dp {
    val currentFallback by rememberUpdatedState(fallback)
    val target by remember(pagerState, heights) {
        derivedStateOf {
            val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
            val from = floor(position).toInt()
            val a = heights[from]
            val b = heights[from + 1]
            lerp(a ?: b ?: currentFallback, b ?: a ?: currentFallback, position - from)
        }
    }
    val height = remember { Animatable(target, Dp.VectorConverter) }
    LaunchedEffect(height) {
        snapshotFlow { target to pagerState.isScrollInProgress }.collectLatest { (h, scrolling) ->
            if (scrolling) height.snapTo(h) else height.animateTo(h)
        }
    }
    return height.value
}

/** A deliberate jump (Today, jump-to-date): animated, or snapped under reduced motion. */
suspend fun PagerState.jumpToPage(
    page: Int,
    reduceMotion: Boolean,
    spec: FiniteAnimationSpec<Float>,
) {
    if (reduceMotion) scrollToPage(page) else animateScrollToPage(page, animationSpec = spec)
}
