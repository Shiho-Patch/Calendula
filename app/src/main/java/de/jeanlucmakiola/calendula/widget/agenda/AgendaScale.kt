package de.jeanlucmakiola.calendula.widget.agenda

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Size tiers the agenda widget scales its typography and row metrics across (#51).
 *
 * The widget uses [androidx.glance.appwidget.SizeMode.Exact], so the composition
 * sees the widget's live size via `LocalSize.current`; [scaleFor] buckets that
 * into one of these tiers and [metricsFor] returns the sizes to draw with. Kept
 * in a pure, Glance-free file (only `compose.ui.unit`) so the bucketing and the
 * "default size is unchanged" invariant are covered by plain JVM tests.
 */
internal enum class AgendaScale { COMPACT, REGULAR, LARGE, XLARGE }

/**
 * Every size the agenda widget varies by tier. Values that don't need to grow
 * (horizontal paddings, the 5.dp stripe width, corner radii) stay inline literals
 * in the widget rather than routing through here.
 */
internal data class AgendaMetrics(
    val title: TextUnit,       // header "Upcoming"
    val dayHeader: TextUnit,   // day label ("Today · …")
    val eventTitle: TextUnit,  // event title line
    val eventTime: TextUnit,   // time/location line
    val placeholder: TextUnit, // "no more events today" (#35)
    val message: TextUnit,     // empty/permission centred message
    val stripeH: Dp,           // coloured event stripe height
    val iconImage: Dp,         // header action icon glyph
    val iconBox: Dp,           // header action touch target
    val rowVPad: Dp,           // event row vertical padding
)

/**
 * [AgendaMetrics] per tier. [AgendaScale.COMPACT] reproduces the widget's original
 * hardcoded constants **verbatim** — so at the default/small size nothing changes;
 * a JVM test pins this against the baseline. Larger tiers scale up by roughly
 * 1.12× / 1.28× / 1.45× (starting points to refine on-device).
 */
internal fun metricsFor(scale: AgendaScale): AgendaMetrics = when (scale) {
    AgendaScale.COMPACT -> AgendaMetrics(
        title = 16.sp,
        dayHeader = 13.sp,
        eventTitle = 14.sp,
        eventTime = 12.sp,
        placeholder = 14.sp,
        message = 14.sp,
        stripeH = 36.dp,
        iconImage = 22.dp,
        iconBox = 40.dp,
        rowVPad = 4.dp,
    )
    AgendaScale.REGULAR -> AgendaMetrics(
        title = 18.sp,
        dayHeader = 14.sp,
        eventTitle = 15.sp,
        eventTime = 13.sp,
        placeholder = 15.sp,
        message = 15.sp,
        stripeH = 40.dp,
        iconImage = 24.dp,
        iconBox = 44.dp,
        rowVPad = 5.dp,
    )
    AgendaScale.LARGE -> AgendaMetrics(
        title = 20.sp,
        dayHeader = 16.sp,
        eventTitle = 17.sp,
        eventTime = 14.sp,
        placeholder = 16.sp,
        message = 16.sp,
        stripeH = 46.dp,
        iconImage = 26.dp,
        iconBox = 48.dp,
        rowVPad = 6.dp,
    )
    AgendaScale.XLARGE -> AgendaMetrics(
        title = 22.sp,
        dayHeader = 18.sp,
        eventTitle = 19.sp,
        eventTime = 15.sp,
        placeholder = 18.sp,
        message = 18.sp,
        stripeH = 52.dp,
        iconImage = 28.dp,
        iconBox = 52.dp,
        rowVPad = 8.dp,
    )
}

/**
 * Buckets a live widget size into an [AgendaScale] by **width**.
 *
 * Width is the right axis: it governs how much of a title fits on a row, so it's
 * what should drive type size. Height only decides how many rows are visible — a
 * tall, narrow widget wants *more events*, not bigger text — so it never raises
 * the tier. It does act as a **cap**, though: a very short widget is stepped back
 * down so it can't keep oversized type in a sliver of space.
 *
 * The thresholds are spread across the width range a phone can actually produce
 * (~180dp up to roughly the screen width) rather than over a theoretical range, so
 * the tiers are reachable in practice. Calibrated on-device (Pixel / Nova): a
 * compact 222dp-wide widget stays COMPACT (the app's baseline, unchanged) and a
 * large 378dp-wide one reaches LARGE. XLARGE is reserved for genuinely wide
 * surfaces — tablets, foldables, landscape — where the extra size reads well.
 */
internal fun scaleFor(size: DpSize): AgendaScale {
    val byWidth = when {
        size.width < 260.dp -> AgendaScale.COMPACT
        size.width < 330.dp -> AgendaScale.REGULAR
        size.width < 420.dp -> AgendaScale.LARGE
        else -> AgendaScale.XLARGE
    }
    // Height can only ever pull the tier *down*, never push it up: a squashed
    // widget would otherwise keep the big type its width earned and look absurd
    // in the little space left. Keeping this a cap (rather than a second scaling
    // axis) is what preserves "tall and narrow shows more events, not bigger text".
    val heightCap = when {
        size.height < 220.dp -> AgendaScale.COMPACT
        size.height < 320.dp -> AgendaScale.REGULAR
        size.height < 420.dp -> AgendaScale.LARGE
        else -> AgendaScale.XLARGE
    }
    return minOf(byWidth, heightCap)
}
