package de.jeanlucmakiola.calendula.ui.onboarding

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
 * The M3 Expressive spatial spring the wizard's steps travel on. Read in
 * composable scope so it can be captured by `AnimatedContent`'s non-composable
 * transitionSpec lambda.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberOnboardingSlideSpec(): FiniteAnimationSpec<IntOffset> =
    MaterialTheme.motionScheme.defaultSpatialSpec()

/**
 * Moving between wizard steps (#163): the steps sit side by side and the whole
 * width swipes across, position on a spring so the arriving step settles with a
 * little give rather than stopping dead.
 *
 * No cross-fade, unlike the calendar's shared-axis paging: two steps have
 * nothing in common, so fading one through the other smears. Both are opaque and
 * travel in lockstep, showing one screen pushing the other off.
 *
 * @param slideDir     +1 = forward (incoming from the right), -1 = back.
 * @param spec         spatial spec, typically [rememberOnboardingSlideSpec].
 * @param fadeSpec     effects spec used for the whole transition under reduced
 *                     motion.
 * @param reduceMotion when true, drop the movement and cross-fade alone.
 */
internal fun onboardingSlideTransition(
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
        targetContentEnter = slideInHorizontally(spec) { w -> dir * w },
        initialContentExit = slideOutHorizontally(spec) { w -> -dir * w },
        // Clipping to the animating container would shear both steps against the
        // viewport edge as they pass; there is no size change here to contain.
        sizeTransform = SizeTransform(clip = false),
    )
}
