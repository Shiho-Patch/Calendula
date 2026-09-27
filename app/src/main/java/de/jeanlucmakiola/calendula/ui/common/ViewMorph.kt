package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import de.jeanlucmakiola.calendula.domain.EventInstance
import kotlinx.datetime.LocalDate

/**
 * What the views share when switching between them (#184): an event visible in
 * both grows from where it sat into where it lands, a date label travels to its
 * new seat, and everything else fades through.
 *
 * An element is tagged with the day it is drawn *from*, not just its instance: a
 * multi-day event is drawn once per day, per week row or per timeline column,
 * and only one element per key may take part. The segment that starts on the
 * same day in both views is the one that travels; the rest fade where they are.
 */
sealed interface ViewMorphKey {
    data class Event(val eventId: Long, val beginMillis: Long, val day: LocalDate) : ViewMorphKey

    data class Day(val date: LocalDate) : ViewMorphKey

    companion object {
        /** [event]'s occurrence as drawn from [day]. */
        fun event(event: EventInstance, day: LocalDate): Event =
            Event(event.eventId, event.start.toEpochMilliseconds(), day)
    }
}

/** The view switch's scopes and specs, captured where they exist. Null under reduced motion. */
@OptIn(ExperimentalSharedTransitionApi::class)
class ViewMorphScope(
    val shared: SharedTransitionScope,
    val visibility: AnimatedVisibilityScope,
    val boundsTransform: BoundsTransform,
    val fadeSpec: FiniteAnimationSpec<Float>,
)

val LocalViewMorph = compositionLocalOf<ViewMorphScope?> { null }

/**
 * Whether a pager page may carry the morph tags: only the settled one. Its
 * neighbours are composed ahead and repeat some of its dates, which would both
 * claim its keys and fly in from off screen.
 */
fun morphPageActive(page: Int, settledPage: Int): Boolean = page == settledPage

/** Provides the view morph to [content] only while [active] — see [morphPageActive]. */
@Composable
fun ViewMorphPage(active: Boolean, content: @Composable () -> Unit) {
    val morph = LocalViewMorph.current
    CompositionLocalProvider(LocalViewMorph provides morph.takeIf { active }, content = content)
}

/**
 * Tags an event chip, bar, block or row. Its bounds are shared and the two faces
 * cross-fade inside them; remeasured rather than scaled, so a title keeps its
 * real size and is clipped while there is no room, instead of smearing.
 *
 * Goes after the element's offset and before its size, so the travelling bounds
 * drive the measurement.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.morphEvent(key: ViewMorphKey.Event): Modifier {
    val morph = LocalViewMorph.current ?: return this
    return with(morph.shared) {
        this@morphEvent.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = morph.visibility,
            enter = fadeIn(morph.fadeSpec),
            exit = fadeOut(morph.fadeSpec),
            boundsTransform = morph.boundsTransform,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            zIndexInOverlay = 1f,
        )
    }
}

/** Tags a day's number, which is small on both sides, so it scales on its way. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.morphDay(date: LocalDate): Modifier {
    val morph = LocalViewMorph.current ?: return this
    return with(morph.shared) {
        this@morphDay.sharedBounds(
            sharedContentState = rememberSharedContentState(ViewMorphKey.Day(date)),
            animatedVisibilityScope = morph.visibility,
            enter = fadeIn(morph.fadeSpec),
            exit = fadeOut(morph.fadeSpec),
            boundsTransform = morph.boundsTransform,
        )
    }
}

/** The view switch's travel: the M3 Expressive default spatial spring, as the pagers settle. */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberViewMorphBoundsTransform(): BoundsTransform {
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    return remember(spec) { BoundsTransform { _, _ -> spec } }
}
