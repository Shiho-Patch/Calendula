package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection

/** How an event chip's title should overflow: what to pass to `Text`. */
data class EventTitleOverflow(val overflow: TextOverflow, val softWrap: Boolean)

/**
 * Overflow for an event chip's title (#164). Chips are narrow enough that the
 * "…" costs a couple of readable characters, so the title runs to the chip's
 * edge and clips mid-glyph instead.
 *
 * Two cases keep the ellipsis:
 *
 * - **[rtl].** With `softWrap` off Compose lays the line out at its full
 *   intrinsic width and clips to the node's left edge, which in RTL is the *end*
 *   of the string — an Arabic title would lose its beginning. The ellipsis
 *   truncates at the logical end in both directions.
 * - **More than one line** ([singleLine] false). Wrapping needs `softWrap` on,
 *   and clipping with it on breaks the last line at the last whole word — less
 *   title than the ellipsis showed, not more.
 */
fun eventTitleOverflowFor(rtl: Boolean, singleLine: Boolean): EventTitleOverflow =
    if (rtl || !singleLine) {
        EventTitleOverflow(TextOverflow.Ellipsis, softWrap = true)
    } else {
        EventTitleOverflow(TextOverflow.Clip, softWrap = false)
    }

/** [eventTitleOverflowFor] against the current layout direction. */
@Composable
fun eventTitleOverflow(singleLine: Boolean = true): EventTitleOverflow =
    eventTitleOverflowFor(
        rtl = LocalLayoutDirection.current == LayoutDirection.Rtl,
        singleLine = singleLine,
    )
