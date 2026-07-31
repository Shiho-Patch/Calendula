package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import de.jeanlucmakiola.calendula.domain.color.eventTone
import de.jeanlucmakiola.calendula.domain.color.inkFor
import de.jeanlucmakiola.calendula.domain.color.oklchOf

/**
 * Whether calendar/event colours are harmonised — hue kept, lightness re-pinned
 * per role — before display (issue #36). Provided app-wide from the setting; the
 * default `true` is the app's own look. When off, the raw provider colour is
 * painted verbatim, matching the sync source (DAVx5/CalDAV) and other calendar
 * apps. Widgets live outside this composition and read the preference directly,
 * then pass the flag through.
 */
val LocalSoftenColors = staticCompositionLocalOf { true }

/**
 * Fill for a surface that carries text on it — a week/day block, a month bar, a
 * widget row, a picker swatch. Pair it with [eventInk].
 */
fun eventFill(rawArgb: Int, dark: Boolean, soften: Boolean): Color =
    Color(eventTone(rawArgb, dark, soften).container)

/**
 * Colour for a mark on an ordinary app surface — a day dot, an agenda or search
 * stripe, a calendar icon tint, the detail header. Same identity as [eventFill],
 * pinned to contrast with the surface instead of with ink.
 */
fun eventAccent(rawArgb: Int, dark: Boolean, soften: Boolean): Color =
    Color(eventTone(rawArgb, dark, soften).accent)

/**
 * Ink for text drawn on an [eventFill]. [alpha] carries the caller's emphasis.
 *
 * Takes the fill rather than the raw colour so it stays correct for the raw
 * (un-harmonised) path too, where the provider decides the lightness.
 */
fun eventInk(fill: Color, alpha: Float = 0.8f): Color =
    Color(inkFor(oklchOf(fill.toArgbInt()).lightness)).copy(alpha = alpha)

/**
 * Ink alpha for a block's secondary line (the time under the title). Held above
 * a fainter value because harmonised containers are deep: the small time text
 * needs to stay clear of the 4.5:1 WCAG asks of body text.
 */
const val SECONDARY_INK_ALPHA = 0.8f

private fun Color.toArgbInt(): Int =
    (0xFF shl 24) or
        ((red * 255f).toInt().coerceIn(0, 255) shl 16) or
        ((green * 255f).toInt().coerceIn(0, 255) shl 8) or
        (blue * 255f).toInt().coerceIn(0, 255)
