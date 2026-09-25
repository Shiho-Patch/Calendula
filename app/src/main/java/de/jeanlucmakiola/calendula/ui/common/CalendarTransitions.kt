package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.core.FiniteAnimationSpec
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
 * here is only what's specific to paging the calendar grid: the specs its
 * pagers settle with, and that the view switch reuses.
 */

/**
 * The M3 Expressive spatial spring the calendar's motion is built on: the
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

/** The same spring as [rememberCalendarSlideSpec], for a pager settling onto its page. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberCalendarPageSpec(): FiniteAnimationSpec<Float> =
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
