package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
    val clip: BelowTopBarClip,
    val stayingPut: StayingPut,
    /** Read while matching, so a tag can sit out without leaving the modifier chain. */
    val enabled: () -> Boolean = { true },
) {
    /** This scope with its tags switched off while [enabled] is false. */
    fun gated(enabled: () -> Boolean): ViewMorphScope =
        ViewMorphScope(shared, visibility, boundsTransform, fadeSpec, clip, stayingPut, enabled)

    internal val config = object : SharedTransitionScope.SharedContentConfig {
        override val SharedTransitionScope.SharedContentState.isEnabled: Boolean
            get() = enabled()
    }
}

/**
 * The events a view has but keeps out of the switch, being scrolled out of
 * sight: the other view leaves them untagged too, or its half would travel to
 * nowhere and snap into place at the end.
 */
class StayingPut {
    val keys = mutableStateSetOf<ViewMorphKey.Event>()
}

/** Marks [key] as staying put while [stays], for as long as it is composed. */
@Composable
fun StayPut(key: ViewMorphKey.Event, stays: Boolean) {
    val stayingPut = LocalViewMorph.current?.stayingPut ?: return
    if (!stays) return
    DisposableEffect(stayingPut, key) {
        stayingPut.keys += key
        onDispose { stayingPut.keys -= key }
    }
}

/**
 * Keeps what travels below the top bar, which every view has at the same height:
 * the overlay draws above the whole screen, so an event scrolled under the bar
 * would otherwise cross it. [top] is the bar's bottom edge in the shared layout.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
class BelowTopBarClip : SharedTransitionScope.OverlayClip {
    var top = 0f

    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path = Path().apply { addRect(Rect(-FAR, top, FAR, FAR)) }

    private companion object {
        const val FAR = 1e6f
    }
}

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
    if (key in morph.stayingPut.keys) return this
    return with(morph.shared) {
        this@morphEvent.sharedBounds(
            sharedContentState = rememberSharedContentState(key, morph.config),
            animatedVisibilityScope = morph.visibility,
            enter = fadeIn(morph.fadeSpec),
            exit = fadeOut(morph.fadeSpec),
            boundsTransform = morph.boundsTransform,
            clipInOverlayDuringTransition = morph.clip,
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
            sharedContentState = rememberSharedContentState(ViewMorphKey.Day(date), morph.config),
            animatedVisibilityScope = morph.visibility,
            enter = fadeIn(morph.fadeSpec),
            exit = fadeOut(morph.fadeSpec),
            boundsTransform = morph.boundsTransform,
            clipInOverlayDuringTransition = morph.clip,
            zIndexInOverlay = 1f,
        )
    }
}

/** The view switch's travel, on the same spring as the timeline's columns resizing. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun rememberViewMorphBoundsTransform(): BoundsTransform {
    val spec = rememberViewSwitchSpec<Rect>()
    return remember(spec) { BoundsTransform { _, _ -> spec } }
}
