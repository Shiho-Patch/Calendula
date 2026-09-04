package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import de.jeanlucmakiola.floret.locale.currentLocale

/**
 * How an event's time is set against its title (#219).
 *
 * The label styles carry a medium weight and 0.5sp of tracking, which left a
 * time reading as part of the title next to it. The time steps down to regular
 * and closes the tracking up, so the two read as a time and a title — whether
 * they sit on one line, as they do on a wide month chip, or on two, as they do
 * in a timed block.
 *
 * The ink is not dimmed further to do it: at this size a fainter grey would
 * give up the contrast [SECONDARY_INK_ALPHA] exists to hold, so the weight
 * carries the difference instead.
 */
private val TIME_WEIGHT = FontWeight.Normal
private val TIME_TRACKING = 0.sp

/** Ink for a title, against [SECONDARY_INK_ALPHA] for the time beside it. */
const val TITLE_INK_ALPHA = 0.85f

/** [this] set as an event's time rather than as its title. */
fun TextStyle.asEventTime(): TextStyle =
    copy(fontWeight = TIME_WEIGHT, letterSpacing = TIME_TRACKING)

/** The same, for a time that shares one line with the title after it. */
private val TIME_SPAN = SpanStyle(fontWeight = TIME_WEIGHT, letterSpacing = TIME_TRACKING)

/** Text reading [time], set apart in [timeInk], before [title]. */
fun inlineTimeLabel(time: String?, title: String, timeInk: Color): AnnotatedString =
    buildAnnotatedString {
        if (time != null) {
            withStyle(TIME_SPAN.copy(color = timeInk)) { append(time) }
            append(" ")
        }
        append(title)
    }

/**
 * The characters of title that have to survive a time prefix for it to be worth
 * its place — the threshold is a readable title, so it is stated in title
 * characters and priced at the surface's own text style rather than guessed in
 * dp. Lowercase Latin of average advance: not "iii", which would let the time
 * in where nothing else fits, nor "WWW", which would keep it out of a column
 * with room to spare.
 *
 * Seven rather than a rounder eight because the 12-hour convention spends a
 * meridiem the 24-hour one does not, and eight would have cost a landscape
 * phone the time in 12-hour while granting it in 24-hour. A narrow portrait
 * column is nowhere near either figure, so the gate is unchanged where it
 * matters most.
 */
private const val TITLE_SAMPLE = "notepad"

/** The widest wall-clock time in either convention: two-digit hour, and a meridiem in 12-hour. */
private const val SAMPLE_HOUR = 12
private const val SAMPLE_MINUTE = 45

/**
 * The narrowest run of [style] text that may carry a time in front of a title:
 * wide enough for the widest time in the current convention plus [TITLE_SAMPLE].
 *
 * Measured rather than a device breakpoint, so it follows the font scale, the
 * 12/24-hour setting, the locale's own time format and whatever else has
 * already been taken off the width — landscape, an unfolded foldable and a
 * tablet all come out wide enough without any of them being named.
 */
@Composable
fun rememberInlineTimeWidth(style: TextStyle): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val locale = currentLocale()
    val sample = formatTimeOfDay(SAMPLE_HOUR, SAMPLE_MINUTE, LocalUse24HourFormat.current, locale)
    return remember(sample, style, density, measurer) {
        val label = inlineTimeLabel(sample, TITLE_SAMPLE, Color.Unspecified)
        with(density) { measurer.measure(label, style).size.width.toDp() }
    }
}
