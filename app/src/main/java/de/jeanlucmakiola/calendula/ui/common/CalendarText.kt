package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp

/**
 * The calendar surfaces' line-height treatment. Material wraps an 11sp glyph in
 * a 16sp line box — room a label wants when it stands on its own, and close to
 * a fifth of an event row when it doesn't. The outer edges only, so a wrapped
 * title keeps its interior line spacing (#190).
 *
 * Trimmed rather than set to a smaller line height: the font picker can load a
 * serif, a monospace or a file of the user's own, and a line height under a
 * face's own ascent and descent overlaps its lines. There is nothing to trim
 * below that, so this is safe whatever font is chosen.
 */
private val TrimmedLines = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

/** Ascenders and descenders both, so a line is measured at its full extent. */
private const val LINE_SAMPLE = "Ag"

/** [this] with Material's outer leading trimmed — see [TrimmedLines]. */
fun TextStyle.trimmedLines(): TextStyle = copy(lineHeightStyle = TrimmedLines)

/** What one trimmed line of [style] actually draws in. */
@Composable
fun rememberTrimmedLineHeight(style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(style, density, measurer) {
        with(density) { measurer.measure(LINE_SAMPLE, style.trimmedLines()).size.height.toDp() }
    }
}
