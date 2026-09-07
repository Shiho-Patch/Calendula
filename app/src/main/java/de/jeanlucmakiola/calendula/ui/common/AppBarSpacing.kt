package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.ui.unit.dp

/**
 * Trailing-edge and title spacing shared by the four calendar app bars (#165).
 *
 * Only the trailing edge and the title's start are shared; the leading edge and
 * the gaps between the action icons stay on M3's defaults, which derive both
 * from the icon buttons' width.
 */
object AppBarSpacing {

    /** Side inset shared by the bars' trailing edge and the month grid. */
    val Inset = 8.dp

    /**
     * Start inset on the title, taking it clear of the navigation icon M3 places
     * it flush against. Real padding rather than a negative offset, which would
     * swallow taps meant for the menu button.
     */
    val TitleInset = 8.dp

    /** M3's own padding around the actions row. */
    internal val BarPadding = 4.dp

    private val IconButtonSize = 48.dp

    /** M3's icon slot inside an icon button; the today glyph fills the same box. */
    internal val IconSize = 24.dp

    /**
     * End padding for a container-backed control that ends the bar, measured to
     * its background. Coerced because [androidx.compose.foundation.layout.padding]
     * throws on a negative value.
     */
    val ContainerTrailingInset = (Inset - BarPadding).coerceAtLeast(0.dp)

    /** Screen edge to the glyph of an icon button that ends the bar. */
    val IconTrailingInset = BarPadding + (IconButtonSize - IconSize) / 2
}
