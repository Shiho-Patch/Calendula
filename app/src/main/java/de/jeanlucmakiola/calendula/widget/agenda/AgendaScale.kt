package de.jeanlucmakiola.calendula.widget.agenda

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jeanlucmakiola.calendula.widget.WidgetScale

/**
 * Horizontal layout constants for an agenda event row. These don't scale with the
 * tier — a wider stripe or gap would eat title width, which is the thing the row
 * is short of — but [TEXT_INDENT] is *derived* from them so the day header and
 * the "nothing left today" line can never drift out of alignment with the event
 * title column the way a hardcoded 19dp could.
 */
internal val ROW_H_PAD = 4.dp
internal val STRIPE_WIDTH = 5.dp
internal val STRIPE_GAP = 10.dp

/** Left indent that lines non-event text up with the event title column. */
internal val TEXT_INDENT = ROW_H_PAD + STRIPE_WIDTH + STRIPE_GAP

/**
 * The width band a *default* agenda placement can land in, per
 * `app/src/main/res/xml/appwidget_info_agenda.xml` (`android:minWidth="180dp"`,
 * `android:targetCellWidth="3"`). Measured at 222dp on a Pixel running Nova, but
 * launcher cell grids vary, so the whole band — not one measured point — has to
 * stay [WidgetScale.COMPACT] for the "default size is unchanged" promise of #51
 * to hold. A test pins that.
 *
 * If the provider's `targetCellWidth` ever changes, this band and the first
 * width threshold in [de.jeanlucmakiola.calendula.widget.scaleFor] must be
 * revisited together.
 */
internal val AGENDA_DEFAULT_WIDTH_BAND = 180.dp..255.dp

/**
 * Every size the agenda widget varies by tier. Values that genuinely shouldn't
 * grow (the horizontal row constants above, corner radii) stay constants rather
 * than routing through here.
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
     * Resolves the stripe height against the user's system font scale.
     *
     * The stripe is a [Dp] but the two text lines it sits beside are `sp`, so
     * they scale with the accessibility font setting and the stripe does not —
     * at "Largest" the text outgrows the stripe and it visibly under-runs the row
     * it is supposed to mark. Multiplying by the same factor keeps them locked,
     * and at the default scale of 1.0 reproduces the tier's value exactly.
     */
    fun scaledForFont(fontScale: Float): AgendaMetrics =
        if (fontScale == 1f) this else copy(stripeH = stripeH * fontScale)
}

/*
 * Type sizes are anchored to the Material 3 type scale (see the `material-3`
 * skill's typography reference) rather than invented: 16sp is Title Medium, 14sp
 * Body Medium / Title Small, 12sp Body Small, 16sp Body Large, 22sp Title Large.
 *
 * Two documented deviations:
 *
 *  ‡ COMPACT's 13sp day header is off-scale. It is held there deliberately —
 *    COMPACT reproduces the widget's original constants verbatim so a
 *    default-sized widget looks exactly as it did (#51), and snapping it to
 *    Title Small (14sp) would break that promise for a 1sp gain.
 *
 *  † Above Title Medium the M3 scale jumps 16 → 22 → 24 with nothing in between,
 *    which is far too coarse for four widget tiers. Where a role would force a
 *    ≥1.4x step between adjacent tiers we hold an interpolated value instead and
 *    mark it. The endpoints stay on real roles.
 */

private val COMPACT_METRICS = AgendaMetrics(
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

private val REGULAR_METRICS = AgendaMetrics(
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

private val XLARGE_METRICS = AgendaMetrics(
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

/** Indexed by [WidgetScale.ordinal] so lookup allocates nothing per recomposition. */
private val AGENDA_METRICS = listOf(
    COMPACT_METRICS,
    REGULAR_METRICS,
    LARGE_METRICS,
    XLARGE_METRICS,
)

internal fun metricsFor(scale: WidgetScale): AgendaMetrics = AGENDA_METRICS[scale.ordinal]
