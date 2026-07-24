package de.jeanlucmakiola.calendula.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * Size tiers a widget scales its typography and metrics across (#51).
 *
 * Shared by every Glance widget so the bucketing rule can't drift between them:
 * each widget keeps its own metrics table, but they all agree on *when* a widget
 * counts as compact, regular, large or extra-large. Both widgets already declare
 * [androidx.glance.appwidget.SizeMode.Exact], so the composition sees the live
 * size via `LocalSize.current` and passes it to [scaleFor].
 *
 * Kept in a pure, Glance-free file (only `compose.ui.unit`) so the bucketing is
 * covered by plain JVM tests.
 */
internal enum class WidgetScale { COMPACT, REGULAR, LARGE, XLARGE }

/**
 * Chrome a widget spends before its first content row: outer vertical padding
 * plus a header row and its spacer. Subtracted from the raw height so the height
 * thresholds below talk about *usable* space rather than gross widget height.
 */
private val CHROME_HEIGHT = 60.dp

/**
 * Buckets a live widget size into a [WidgetScale] by **width**.
 *
 * Width is the right axis: it governs how much of a title fits on a row, so it's
 * what should drive type size. Height only decides how many rows are visible — a
 * tall, narrow widget wants *more events*, not bigger text — so it never raises
 * the tier. It does act as a **cap**, though: a genuinely squashed widget is
 * stepped back down so it can't keep oversized type in a sliver of space.
 *
 * The width thresholds are spread across the range a phone can actually produce
 * (~180dp up to roughly the screen width) rather than over a theoretical range,
 * so the tiers are reachable in practice. Calibrated on-device (Pixel / Nova): a
 * compact 222dp-wide widget stays COMPACT (the app's baseline, unchanged) and a
 * full-width 378dp one reaches LARGE. XLARGE is reserved for genuinely wide
 * surfaces — tablets, foldables, landscape — where the extra size reads well.
 *
 * The height cap is deliberately generous: it exists to catch a widget squashed
 * to one or two rows, **not** to gate ordinary placements. A full-width widget at
 * the usual three cells tall (~270dp) must still reach the tier its width earned
 * — that is exactly the resize #51 reports, and an aggressive cap would make the
 * whole feature a no-op for it.
 */
internal fun scaleFor(size: DpSize): WidgetScale {
    val byWidth = when {
        size.width < 260.dp -> WidgetScale.COMPACT
        size.width < 330.dp -> WidgetScale.REGULAR
        size.width < 420.dp -> WidgetScale.LARGE
        else -> WidgetScale.XLARGE
    }
    // Height can only ever pull the tier *down*, never push it up: a squashed
    // widget would otherwise keep the big type its width earned and look absurd
    // in the little space left. Keeping this a cap (rather than a second scaling
    // axis) is what preserves "tall and narrow shows more events, not bigger
    // text". Thresholds are usable height — roughly one, two and three rows of
    // breathing room once the header is paid for.
    val usable = size.height - CHROME_HEIGHT
    val heightCap = when {
        usable < 70.dp -> WidgetScale.COMPACT
        usable < 130.dp -> WidgetScale.REGULAR
        usable < 200.dp -> WidgetScale.LARGE
        else -> WidgetScale.XLARGE
    }
    return minOf(byWidth, heightCap)
}
