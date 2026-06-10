package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset

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
 * Horizontal slide for navigating between adjacent months/weeks.
 *
 * @param slideDir +1 = forward (incoming from the right), -1 = back, 0 = jump
 *                 (e.g. "today"); a jump reuses the forward direction.
 * @param spec     spatial animation spec, typically [rememberCalendarSlideSpec].
 */
fun calendarSlideTransition(
    slideDir: Int,
    spec: FiniteAnimationSpec<IntOffset>,
): ContentTransform {
    val dir = if (slideDir == 0) 1 else slideDir
    return slideInHorizontally(spec) { w -> dir * w }
        .togetherWith(slideOutHorizontally(spec) { w -> -dir * w })
}
