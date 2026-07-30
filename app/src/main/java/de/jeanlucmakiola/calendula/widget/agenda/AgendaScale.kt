package de.jeanlucmakiola.calendula.widget.agenda

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jeanlucmakiola.calendula.widget.WidgetSize

/**
 * Horizontal layout constants for an agenda event row. These don't scale with the
 * size step, since a wider stripe or gap would eat title width, but [TEXT_INDENT]
 * is derived from them so non-event text can't drift out of alignment.
 */
internal val ROW_H_PAD = 4.dp
internal val STRIPE_WIDTH = 5.dp
internal val STRIPE_GAP = 10.dp

/** Left indent that lines non-event text up with the event title column. */
internal val TEXT_INDENT = ROW_H_PAD + STRIPE_WIDTH + STRIPE_GAP

/**
 * Every size the agenda widget varies by [WidgetSize]. Values that genuinely
 * shouldn't grow (the horizontal row constants above, corner radii) stay
 * constants rather than routing through here.
 */
internal data class AgendaMetrics(
    val title: TextUnit,        // header "Upcoming"
    val dayHeader: TextUnit,    // day label ("Today · …")
    val eventTitle: TextUnit,   // event title line
    val eventTime: TextUnit,    // time/location line
    val placeholder: TextUnit,  // "no more events today" (#35)
    val message: TextUnit,      // empty/permission centred message
    val stripeH: Dp,            // coloured event stripe height
    val iconImage: Dp,          // header action icon glyph
    val iconBox: Dp,            // header action touch target
    val rowVPad: Dp,            // event row vertical padding
    val dayHeaderTopPad: Dp,    // space above a day header
) {
    /**
     * Resolves the stripe height against the user's system font scale: the stripe
     * is a [Dp] while the lines beside it are `sp`, so without this the text
     * outgrows it at large font settings.
     */
    fun scaledForFont(fontScale: Float): AgendaMetrics =
        if (fontScale == 1f) this else copy(stripeH = stripeH * fontScale)
}

/*
 * Type sizes are anchored to the Material 3 type scale, with two deviations:
 *
 *  ‡ SMALL's 13sp day header is off-scale, held there so SMALL reproduces the
 *    widget's original constants verbatim (#51).
 *  † Above Title Medium the M3 scale jumps 16 → 22 → 24, too coarse for four
 *    steps; interpolated values are used where a role would force a ≥1.4x jump.
 */

private val SMALL_METRICS = AgendaMetrics(
    title = 16.sp,       // M3 Title Medium
    dayHeader = 13.sp,   // ‡ off-scale, holds the #51 baseline
    eventTitle = 14.sp,  // M3 Body Medium
    eventTime = 12.sp,   // M3 Body Small
    placeholder = 14.sp, // M3 Body Medium
    message = 14.sp,     // M3 Body Medium
    stripeH = 36.dp,
    iconImage = 22.dp,
    iconBox = 40.dp,
    rowVPad = 4.dp,
    dayHeaderTopPad = 10.dp,
)

private val MEDIUM_METRICS = AgendaMetrics(
    title = 18.sp,       // †
    dayHeader = 14.sp,   // M3 Title Small
    eventTitle = 16.sp,  // M3 Body Large
    eventTime = 14.sp,   // M3 Body Medium
    placeholder = 16.sp, // M3 Body Large
    message = 16.sp,     // M3 Body Large
    stripeH = 40.dp,
    iconImage = 24.dp,
    iconBox = 44.dp,
    rowVPad = 5.dp,
    dayHeaderTopPad = 11.dp,
)

private val LARGE_METRICS = AgendaMetrics(
    title = 20.sp,       // †
    dayHeader = 16.sp,   // M3 Title Medium
    eventTitle = 18.sp,  // †
    eventTime = 14.sp,   // M3 Body Medium — the secondary line steps more slowly
    placeholder = 18.sp, // †                on purpose, so the title keeps its
    message = 18.sp,     // †                lead and the hierarchy survives.
    stripeH = 46.dp,
    iconImage = 26.dp,
    iconBox = 48.dp,
    rowVPad = 6.dp,
    dayHeaderTopPad = 12.dp,
)

private val EXTRA_LARGE_METRICS = AgendaMetrics(
    title = 22.sp,       // M3 Title Large
    dayHeader = 18.sp,   // †
    eventTitle = 20.sp,  // †
    eventTime = 16.sp,   // M3 Body Large
    placeholder = 20.sp, // †
    message = 20.sp,     // †
    stripeH = 52.dp,
    iconImage = 28.dp,
    iconBox = 52.dp,
    rowVPad = 8.dp,
    dayHeaderTopPad = 14.dp,
)

/** Indexed by [WidgetSize.ordinal] so lookup allocates nothing per recomposition. */
private val AGENDA_METRICS = listOf(
    SMALL_METRICS,
    MEDIUM_METRICS,
    LARGE_METRICS,
    EXTRA_LARGE_METRICS,
)

internal fun metricsFor(size: WidgetSize): AgendaMetrics = AGENDA_METRICS[size.ordinal]
