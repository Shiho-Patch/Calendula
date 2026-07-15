package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import de.jeanlucmakiola.floret.components.pastelize

/**
 * Whether calendar/event colours are softened toward theme-fitting pastels
 * before display (issue #36). Provided app-wide from the "soften colours"
 * setting; the default `true` keeps the historical look. When off, the raw
 * provider colour is painted verbatim — matching the sync source (DAVx5/CalDAV)
 * and other calendar apps. Widgets live outside this composition and read the
 * preference directly, then pass the flag to [eventFill] / [eventInk].
 */
val LocalSoftenColors = staticCompositionLocalOf { true }

/**
 * Display fill for an event chip/bar or a calendar tint: the [pastelize]d colour
 * when [soften] is on, else the raw provider ARGB verbatim (forced opaque, since
 * pastelize also returns an opaque colour).
 */
fun eventFill(rawArgb: Int, dark: Boolean, soften: Boolean): Color =
    if (soften) pastelize(rawArgb, dark) else Color(rawArgb or 0xFF000000.toInt())

/**
 * Contrast ink (title text / glyph) for a filled chip painted with [eventFill]:
 * white on a dark fill, near-black on a light one (issue #21). Applies to both
 * softened and raw fills — a saturated hue (deep blue, purple, red) is
 * perceptually dark even after softening pins its HSV value, so black text on it
 * reads poorly. The choice is objective, not a tuned threshold: white wins only
 * when it out-contrasts black against the fill, which (by the WCAG contrast
 * ratio) is at relative luminance ≈ 0.18 — so mid and light colours keep
 * near-black text. [alpha] carries the caller's soft emphasis.
 */
fun eventInk(fill: Color, alpha: Float = 0.8f): Color {
    val useWhite = fill.luminance() < INK_LUMINANCE_CROSSOVER
    return (if (useWhite) Color.White else Color.Black).copy(alpha = alpha)
}

/**
 * Relative-luminance crossover where white text starts to out-contrast black.
 * Solving `contrast(white, L) = contrast(black, L)` on the WCAG ratio gives
 * `L = sqrt(1.05 * 0.05) - 0.05 ≈ 0.179`.
 */
private const val INK_LUMINANCE_CROSSOVER = 0.179f
