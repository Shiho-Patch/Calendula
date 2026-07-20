package de.jeanlucmakiola.calendula.ui.month

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import kotlinx.datetime.LocalDate

/**
 * What lets the split style's compact grid *become* the full month rather than be
 * swapped for it (#53).
 *
 * The two grids are separate composables — the compact one draws dots, the
 * expanded one draws the paged style's bars and pills — so nothing about them is
 * shared by construction. Tagging the pieces that mean the same thing on both
 * sides with the same [MonthMorphKey] hands Compose enough to animate one into
 * the other: a day's cell grows, its number rides along, and each dot travels out
 * to the bar it stood for.
 *
 * It travels as a composition local rather than as parameters because the pieces
 * sit five levels below where the scopes exist, and because a null default is
 * exactly the right meaning for everyone else: the paged, continuous, and dense
 * styles share the same row and cell composables, provide nothing, and pay
 * nothing. Reduced motion provides nothing either, which leaves the plain
 * cross-fade underneath.
 */
internal sealed interface MonthMorphKey {
    /** A day's background pill — the structural anchor the rest rides on. */
    data class Cell(val date: LocalDate) : MonthMorphKey

    data class DayNumber(val date: LocalDate) : MonthMorphKey

    /**
     * One event *on one day*. Keyed by date as well as instance because a
     * multi-day event has a dot on every day it covers but only one bar, drawn
     * from where it starts: the start day's dot is the one that becomes the bar,
     * and the rest are left unmatched on purpose. They fade where they stand
     * while the bar sweeps out over them, which is the honest reading — one of
     * them could not become the bar without the others teleporting into it.
     */
    data class Event(val date: LocalDate, val instanceId: Long) : MonthMorphKey

    /** The grab handle, which slides from the pane's seam to the foot of the grid. */
    data object Handle : MonthMorphKey
}

@OptIn(ExperimentalSharedTransitionApi::class)
internal class MonthMorphScope(
    val shared: SharedTransitionScope,
    val visibility: AnimatedVisibilityScope,
)

internal val LocalMonthMorph = compositionLocalOf<MonthMorphScope?> { null }

/**
 * How fast a tagged piece travels between its two homes.
 *
 * The *fast* spatial spec, not the default one the month/week/day page slide
 * uses. Paging is a spring under one whole page and wants room to settle; this is
 * a direct answer to a drag the user has already committed to, and at the default
 * pace the grid felt like it was catching up rather than responding. It is also
 * a hundred-odd pieces moving at once, where a long settle reads as lag.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberMorphBoundsTransform(): BoundsTransform {
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Rect>()
    return remember(spec) { BoundsTransform { _, _ -> spec } }
}

/**
 * Tag content that is *the same thing* on both sides — a background pill, a day
 * number — so it animates between its two positions and sizes.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.morphElement(key: MonthMorphKey): Modifier {
    val morph = LocalMonthMorph.current ?: return this
    return with(morph.shared) {
        this@morphElement.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = morph.visibility,
            boundsTransform = rememberMorphBoundsTransform(),
        )
    }
}

/**
 * Tag content that means the same thing but *is drawn differently* on each side —
 * a 5dp dot and a titled bar — so only the bounds are shared and the contents
 * cross-fade inside them.
 *
 * [ResizeMode.RemeasureToBounds][SharedTransitionScope.ResizeMode] rather than
 * scaling: a bar's title laid out at the dot's 5dp and then scaled up would
 * arrive as a smear. Remeasuring keeps the text at its real size throughout and
 * simply clips it while there is no room, so what grows is the pill, not the type.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.morphBounds(key: MonthMorphKey): Modifier {
    val morph = LocalMonthMorph.current ?: return this
    return with(morph.shared) {
        this@morphBounds.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = morph.visibility,
            enter = fadeIn(),
            exit = fadeOut(),
            boundsTransform = rememberMorphBoundsTransform(),
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}
