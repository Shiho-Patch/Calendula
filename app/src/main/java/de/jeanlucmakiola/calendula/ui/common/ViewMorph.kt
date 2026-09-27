package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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
 *
 * A day's ground — its month cell, its timeline column — travels too, so the
 * events never arrive ahead of what they sit on.
 */
sealed interface ViewMorphKey {
    data class Event(val eventId: Long, val beginMillis: Long, val day: LocalDate) : ViewMorphKey

    data class Day(val date: LocalDate) : ViewMorphKey

    data class Ground(val date: LocalDate) : ViewMorphKey

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
    val clip: SharedTransitionScope.OverlayClip,
)

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
    return with(morph.shared) {
        this@morphEvent.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
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
            sharedContentState = rememberSharedContentState(ViewMorphKey.Day(date)),
            animatedVisibilityScope = morph.visibility,
            enter = fadeIn(morph.fadeSpec),
            exit = fadeOut(morph.fadeSpec),
            boundsTransform = morph.boundsTransform,
            clipInOverlayDuringTransition = morph.clip,
            // Over the grounds, which a month cell's number sits inside.
            zIndexInOverlay = 1f,
        )
    }
}

/** Tags a day's ground, under the events and numbers that travel with it. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.morphGround(date: LocalDate): Modifier {
    val morph = LocalViewMorph.current ?: return this
    return with(morph.shared) {
        this@morphGround.sharedBounds(
            sharedContentState = rememberSharedContentState(ViewMorphKey.Ground(date)),
            animatedVisibilityScope = morph.visibility,
            enter = fadeIn(morph.fadeSpec),
            exit = fadeOut(morph.fadeSpec),
            boundsTransform = morph.boundsTransform,
            clipInOverlayDuringTransition = morph.clip,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}

/**
 * The on-screen window of each day's timeline column, painted like the column
 * behind the scrolling one: a whole 24-hour column can't travel, its window can.
 * Nothing is drawn while there is no morph to take part in.
 */
@Composable
fun TimelineGrounds(
    days: List<LocalDate>,
    scrollState: ScrollState,
    hourHeight: Dp,
    columnGap: Dp,
    modifier: Modifier = Modifier,
) {
    if (LocalViewMorph.current == null) return
    val showHourGrid = LocalShowHourGrid.current
    val hourPx = with(LocalDensity.current) { hourHeight.toPx() }
    val cell = MaterialTheme.colorScheme.surfaceContainer
    val ground = if (showHourGrid) MaterialTheme.colorScheme.surface else cell
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(columnGap)) {
        days.forEach { day ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .morphGround(day)
                    .clipToBounds()
                    .background(ground)
                    .hourGridCells(showHourGrid, hourPx, cell) { scrollState.value.toFloat() },
            )
        }
    }
}

/**
 * The pieces of the top bar and the FAB every view has (#184). Those drawn the
 * same in each view hold still through a switch instead of fading out and back
 * in; the title and the view pill, which differ, cross-fade in place.
 */
enum class ChromeSlot(val identical: Boolean) {
    Menu(identical = true),
    Search(identical = true),
    Today(identical = true),
    Create(identical = true),
    Title(identical = false),
    Pill(identical = false),
}

/**
 * Provides the view morph to a screen's top bar or FAB only while [enabled]:
 * with its drawer open or opening, what travels would be drawn over the drawer.
 */
@Composable
fun ViewMorphChrome(enabled: Boolean, content: @Composable () -> Unit) = ViewMorphPage(enabled, content)

/** Tags a piece of the top bar or the FAB; see [ChromeSlot]. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.morphChrome(slot: ChromeSlot): Modifier {
    val morph = LocalViewMorph.current ?: return this
    return with(morph.shared) {
        val state = rememberSharedContentState(slot)
        if (slot.identical) {
            this@morphChrome.sharedElement(
                sharedContentState = state,
                animatedVisibilityScope = morph.visibility,
                boundsTransform = morph.boundsTransform,
                zIndexInOverlay = CHROME_Z_INDEX,
            )
        } else {
            this@morphChrome.sharedBounds(
                sharedContentState = state,
                animatedVisibilityScope = morph.visibility,
                enter = fadeIn(morph.fadeSpec),
                exit = fadeOut(morph.fadeSpec),
                boundsTransform = morph.boundsTransform,
                // Each side keeps its own size: a title squeezed to the other's
                // width would ellipsise and unfold on the way.
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.None, Alignment.CenterStart),
                zIndexInOverlay = CHROME_Z_INDEX,
            )
        }
    }
}

/** Above the events and dates, which pass under the top bar on their way. */
private const val CHROME_Z_INDEX = 2f

/** The view switch's travel: the M3 Expressive default spatial spring, as the pagers settle. */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberViewMorphBoundsTransform(): BoundsTransform {
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    return remember(spec) { BoundsTransform { _, _ -> spec } }
}
