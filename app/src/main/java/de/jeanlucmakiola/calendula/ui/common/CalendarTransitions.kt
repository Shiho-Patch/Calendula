package de.jeanlucmakiola.calendula.ui.common

import android.provider.Settings
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException

/**
 * Whether the user has asked the system to remove animations (Settings →
 * Accessibility → "Remove animations", which sets the global animator duration
 * scale to 0). Compose animations do *not* honour this platform flag on their
 * own, so the shared motion helpers in this file check it and fall back to a
 * quick cross-fade — opacity only, no spatial movement — to respect the
 * vestibular intent of the setting while keeping state changes legible.
 *
 * Read once at composition; the scale changes rarely and only takes full effect
 * after a process restart anyway.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

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

/**
 * Cross-fade [ContentTransform] for swapping whole screens or content blocks
 * where there is no meaningful spatial direction (e.g. onboarding gates). Pure
 * opacity, so it doubles as its own reduced-motion form.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun calendarFadeThrough(): ContentTransform {
    val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    return fadeIn(fade).togetherWith(fadeOut(fade))
}

/**
 * Enter transition for a vertically-revealed section (expandable rows, inline
 * fields): height grows from the top while fading in. Under reduced motion the
 * height growth is dropped, leaving a quick fade.
 *
 * Pair with [calendarCollapseExit]. This is the promoted form of the pattern
 * originally inlined in the event edit form, so every expandable surface in the
 * app reveals the same way.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun calendarExpandEnter(reduceMotion: Boolean = rememberReduceMotion()): EnterTransition {
    val fade = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec())
    return if (reduceMotion) {
        fade
    } else {
        expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fade
    }
}

/** Exit counterpart to [calendarExpandEnter]: shrink + fade, or fade only under reduced motion. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun calendarCollapseExit(reduceMotion: Boolean = rememberReduceMotion()): ExitTransition {
    val fade = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec())
    return if (reduceMotion) {
        fade
    } else {
        shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fade
    }
}

/**
 * Enter transition for content revealed by an `AnimatedContent`/`AnimatedVisibility`
 * (e.g. search results once a query resolves): a gentle rise + fade. Reduced
 * motion keeps the fade only.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun calendarItemEnter(reduceMotion: Boolean = rememberReduceMotion()): EnterTransition {
    val fade = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec())
    return if (reduceMotion) {
        fade
    } else {
        fade + slideInVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { h -> h / 6 }
    }
}

/**
 * Shared [LazyItemScope.animateItem] wiring so list rows fade/relocate with the
 * app's motion scheme instead of Compose's default spring. Returns a bare
 * [Modifier] under reduced motion so rows snap into place. Requires the list to
 * supply stable item keys.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LazyItemScope.calendarAnimateItem(reduceMotion: Boolean = rememberReduceMotion()): Modifier =
    if (reduceMotion) {
        Modifier
    } else {
        Modifier.animateItem(
            fadeInSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            placementSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        )
    }

/**
 * Standard Android predictive-back transform for a full-screen overlay: as the
 * back gesture is dragged, the surface scales toward ~90%, shifts toward the
 * swiped edge and rounds its corners, previewing what's behind. Completing the
 * gesture invokes [onBack]; cancelling springs it back.
 *
 * A drop-in replacement for a screen's own `BackHandler(onBack)` — register it
 * once and apply the returned [Modifier] to that screen's root so the preview
 * respects the same back semantics. Under reduced motion the visual preview is
 * skipped (the back still works); on API < 34 the system delivers no progress,
 * so it degrades to a plain back.
 */
@Composable
fun Modifier.predictiveBack(
    onBack: () -> Unit,
    enabled: Boolean = true,
    reduceMotion: Boolean = rememberReduceMotion(),
): Modifier {
    val progress = remember { Animatable(0f) }
    var fromLeftEdge by remember { mutableStateOf(true) }

    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event ->
                fromLeftEdge = event.swipeEdge == BackEventCompat.EDGE_LEFT
                progress.snapTo(FastOutSlowInEasing.transform(event.progress))
            }
            onBack()
            progress.snapTo(0f)
        } catch (_: CancellationException) {
            progress.animateTo(0f)
        }
    }

    if (reduceMotion) return this
    return this.graphicsLayer {
        val p = progress.value
        val scale = 1f - 0.1f * p
        scaleX = scale
        scaleY = scale
        translationX = (if (fromLeftEdge) 1f else -1f) * 24.dp.toPx() * p
        shape = RoundedCornerShape(32.dp.toPx() * p)
        clip = p > 0f
    }
}
