package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.ContentTransform
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
 * The M3 Expressive spatial spring used for the month/week slide: the *fast*
 * spring-physics spec from the active motion scheme — snappy with a subtle
 * springy settle, rather than a fixed easing curve.
 *
 * Read it in a composable scope (this helper) so it can be captured by the
 * non-composable `AnimatedContent` transitionSpec lambda.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberCalendarSlideSpec(): FiniteAnimationSpec<IntOffset> =
    MaterialTheme.motionScheme.fastSpatialSpec()

/**
 * The fast effects spec from the active motion scheme, for opacity (fade)
 * transitions. Captured in composable scope alongside [rememberCalendarSlideSpec]
 * for use in non-composable transition lambdas and as the reduced-motion fallback.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberCalendarFadeSpec(): FiniteAnimationSpec<Float> =
    MaterialTheme.motionScheme.fastEffectsSpec()

/**
 * Horizontal slide for navigating between adjacent months/weeks/days.
 *
 * @param slideDir     +1 = forward (incoming from the right), -1 = back, 0 = jump
 *                     (e.g. "today"); a jump reuses the forward direction.
 * @param spec         spatial animation spec, typically [rememberCalendarSlideSpec].
 * @param fadeSpec     effects spec for the reduced-motion fade, typically
 *                     [rememberCalendarFadeSpec].
 * @param reduceMotion when true, swap the directional slide for a plain cross-fade.
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
    return slideInHorizontally(spec) { w -> dir * w }
        .togetherWith(slideOutHorizontally(spec) { w -> -dir * w })
}
