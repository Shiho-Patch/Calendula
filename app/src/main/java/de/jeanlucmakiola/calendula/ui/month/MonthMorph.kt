package de.jeanlucmakiola.calendula.ui.month

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
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

    /**
     * A day's "+N" marker. The compact grid writes it as a count beside the dots
     * and the expanded one as a row of small dots under the bars, but they stand
     * for the same events and appear on exactly the same days — both sides seat
     * three and overflow the rest — so they are a matched pair, not two unrelated
     * bits of text. Tagged, the marker travels with its day like everything else
     * in the cell.
     */
    data class Overflow(val date: LocalDate) : MonthMorphKey

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
 * True while the grid is mid-morph, for content that has to stop clipping to let
 * the travelling pieces through.
 *
 * A mark's two homes are in *different rows*: the dot for the 20th sits a third
 * of the way down the compact grid, its bar most of the way down the expanded
 * one. Clipped to the row it is arriving at, a mark spends the first half of its
 * journey outside those bounds and is simply not drawn — so it appears from
 * nowhere, part-way through, already near its destination. Rendering in place
 * rather than in an overlay is what subjects them to that clip, and is worth
 * keeping; the clip is what has to yield, and only while pieces are in flight.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun morphInFlight(): Boolean {
    val morph = LocalMonthMorph.current ?: return false
    return morph.shared.isTransitionActive
}

/**
 * Tag content that is *the same thing* on both sides — a background pill, a day
 * number — so it animates between its two positions and sizes.
 *
 * ### Why nothing renders in the overlay
 *
 * By default a travelling piece is painted into an overlay above the *entire*
 * regular tree, so it can fly over anything in its way. That is right for a
 * thumbnail crossing a screen, and wrong here: everything is moving inside one
 * grid, and the overlay meant every untagged neighbour — the selection outline,
 * the "+N" markers — spent the transition buried under pieces that had left the
 * tree's z-order behind. Lifting each of them out in turn fixed the burying and
 * bought a worse problem: they then floated over the grid on their own layer,
 * out of step with it.
 *
 * Rendering in place puts everything back in one z-order and one clip, which is
 * what makes the grid read as a single surface changing shape rather than a
 * stack of pieces sliding past each other. Nothing here needs to escape its
 * ancestors: the cells, marks and markers all travel within the grid.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.morphElement(key: MonthMorphKey): Modifier {
    val morph = LocalMonthMorph.current ?: return this
    return with(morph.shared) {
        this@morphElement.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = morph.visibility,
            renderInOverlayDuringTransition = false,
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
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
            renderInOverlayDuringTransition = false,
        )
    }
}
