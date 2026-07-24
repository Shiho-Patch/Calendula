package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset

/**
 * Calendar-specific motion. The family's generic content transitions — section
 * expand/collapse, item reveal, list relayout, cross-fade, predictive-back, and
 * the reduce-motion check — now live in floret-kit's identity module
 * (`expandEnter`, `collapseExit`, `itemEnter`, `animateItemMotion`,
 * `fadeThrough`, `Modifier.predictiveBack`, `rememberReduceMotion`). What stays
 * here is only what's specific to paging the calendar grid: the directional
 * month/week/day slide and the specs that feed it.
 */

/**
 * The M3 Expressive spatial spring used for the month/week/day slide: the
 * *default* spring-physics spec from the active motion scheme, rather than a
 * fixed easing curve.
 *
 * Default rather than fast: `fastSpatialSpec` is tuned for small, incidental
 * movement, and driving a whole calendar page with it made the settle read as a
 * twitch instead of a glide.
 *
 * Read it in a composable scope (this helper) so it can be captured by the
 * non-composable `AnimatedContent` transitionSpec lambda.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberCalendarSlideSpec(): FiniteAnimationSpec<IntOffset> =
    MaterialTheme.motionScheme.defaultSpatialSpec()

/**
 * The effects spec from the active motion scheme, for the opacity half of the
 * transition. Captured in composable scope alongside [rememberCalendarSlideSpec]
 * for use in non-composable transition lambdas, and reused on its own as the
 * reduced-motion fallback.
 *
 * Opacity gets its own spec on purpose: M3 Expressive springs *position* and
 * eases *opacity*, and a fade that bounced with the slide would shimmer.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberCalendarFadeSpec(): FiniteAnimationSpec<Float> =
    MaterialTheme.motionScheme.defaultEffectsSpec()

/**
 * Navigating between adjacent months/weeks/days, as M3's shared-axis X: the
 * outgoing page slides and fades one way while the incoming one arrives from the
 * other, position on a spring and opacity on an easing curve.
 *
 * @param slideDir     +1 = forward (incoming from the right), -1 = back, 0 = jump
 *                     (e.g. "today"); a jump reuses the forward direction.
 * @param spec         spatial animation spec, typically [rememberCalendarSlideSpec].
 * @param fadeSpec     effects spec for the opacity half, and for the whole
 *                     transition under reduced motion; typically
 *                     [rememberCalendarFadeSpec].
 * @param reduceMotion when true, drop the movement and cross-fade alone.
 */
fun calendarSlideTransition(
    slideDir: Int,
    spec: FiniteAnimationSpec<IntOffset>,
    fadeSpec: FiniteAnimationSpec<Float>,
    reduceMotion: Boolean,
): ContentTransform {
    if (reduceMotion) {
        return fadeIn(fadeSpec).togetherWith(fadeOut(fadeSpec))
    }
    val dir = if (slideDir == 0) 1 else slideDir
    return ContentTransform(
        targetContentEnter =
            slideInHorizontally(spec) { w -> dir * w / SLIDE_TRAVEL_DIVISOR } + fadeIn(fadeSpec),
        initialContentExit =
            slideOutHorizontally(spec) { w -> -dir * w / SLIDE_TRAVEL_DIVISOR } + fadeOut(fadeSpec),
        // AnimatedContent clips to the animating container by default, which
        // shears the pages against the viewport edge as they pass. There is no
        // size change here to contain — both pages are the same grid.
        sizeTransform = SizeTransform(clip = false),
    )
}

/**
 * How far a page travels, as a fraction of the container width.
 *
 * A full width was the obvious reading of "paging", but the two pages are
 * stacked and both opaque, so a full-width slide showed one grid racing across
 * another — the movement carried the whole transition and had a long way to go.
 * Under M3's shared-axis pattern the offset only has to *hint* the direction
 * while the cross-fade does the swapping, so a fifth of the width is plenty and
 * leaves nothing skating past.
 */
private const val SLIDE_TRAVEL_DIVISOR = 5
