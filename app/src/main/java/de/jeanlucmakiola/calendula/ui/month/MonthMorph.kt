package de.jeanlucmakiola.calendula.ui.month

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarFadeSpec
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
        )
    }
}

/**
 * Lift *untagged* content into the shared-transition overlay for the duration of
 * a transition, so the travelling pieces don't bury it.
 *
 * Tagged content is drawn in the overlay, which is painted above the whole
 * regular tree. Anything left in that tree therefore disappears behind it while a
 * transition runs, however it was layered locally — which is what hid the split
 * style's selection outline behind its own cell pill from the first frame of an
 * expand to the last.
 *
 * The content keeps its own layout bounds; only where it is *painted* changes. So
 * the outline still draws at the size the collapsed cell actually is, which is
 * the whole reason it was left untagged.
 *
 * Lifting content out of the regular tree also lifts it out of the enter/exit
 * layer [AnimatedContent][androidx.compose.animation.AnimatedContent] fades the
 * outgoing page with, so it no longer fades at all — it stood at full strength
 * for the whole transition and then blinked out at the end of it. The fade is
 * therefore reapplied here, on the lifted content's own layer, so it travels with
 * it. Anything sent to the overlay needs this, which is why it lives in the
 * helper rather than at the call site.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun Modifier.morphOverlay(
    zIndex: Float = 1f,
    fadeSpec: FiniteAnimationSpec<Float> = rememberCalendarFadeSpec(),
): Modifier {
    val morph = LocalMonthMorph.current ?: return this
    val lifted = with(morph.shared) {
        this@morphOverlay.renderInSharedTransitionScopeOverlay(zIndexInOverlay = zIndex)
    }
    return with(morph.visibility) {
        lifted.animateEnterExit(
            enter = fadeIn(fadeSpec),
            exit = fadeOut(fadeSpec),
            label = "morph-overlay-fade",
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
        )
    }
}
