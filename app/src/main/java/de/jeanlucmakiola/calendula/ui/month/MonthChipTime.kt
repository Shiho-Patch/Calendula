package de.jeanlucmakiola.calendula.ui.month

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.common.LocalUse24HourFormat
import de.jeanlucmakiola.calendula.ui.common.SECONDARY_INK_ALPHA
import de.jeanlucmakiola.calendula.ui.common.formatTimeOfDay
import de.jeanlucmakiola.floret.locale.currentLocale
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Locale

/** Padding between a month chip's edge and its text. */
internal val MONTH_CHIP_TEXT_PADDING = 4.dp

/** A chip's own inset inside its day cell, on top of the cell's gap. */
internal val MONTH_CHIP_INSET = CELL_GAP + 1.dp

/** Horizontal space a chip spends on chrome rather than on text, both sides. */
private val CHIP_CHROME = (MONTH_CHIP_INSET + MONTH_CHIP_TEXT_PADDING) * 2f

/**
 * The seven characters of title a chip has to keep for the time to be worth its
 * place (#219) — the threshold is a readable title, so it is stated in title
 * characters and priced at the chip's own text style rather than guessed in dp.
 * Lowercase Latin of average advance: not "iii", which would let the time in on
 * a column where nothing else fits, nor "WWW", which would keep it out of one
 * with room to spare.
 *
 * Seven rather than a rounder eight because the 12-hour convention spends a
 * meridiem the 24-hour one does not, and eight would have cost a landscape
 * phone the time in 12-hour while granting it in 24-hour. A narrow portrait
 * column is nowhere near either figure, so the gate is unchanged where it
 * matters most.
 */
private const val TITLE_SAMPLE = "notepad"

/** The widest wall-clock time in either convention: two-digit hour, plus a meridiem in 12-hour. */
private const val SAMPLE_HOUR = 12
private const val SAMPLE_MINUTE = 45

/**
 * Ink for a chip's title, against [SECONDARY_INK_ALPHA] for the time in front of
 * it — the same pairing the week and day blocks put a title and its time label
 * in, so a chip that carries both reads the way a block does.
 */
internal const val TITLE_INK_ALPHA = 0.85f

/**
 * How the time is set apart from the title it precedes (#219). labelSmall's
 * medium weight steps down to regular and its 0.5sp tracking closes up, so the
 * two read as a time and a title rather than as one run-on string. Both moves
 * also narrow the prefix, which is worth most on the column that only just
 * earned it.
 *
 * The ink is handed in rather than dimmed further: at this size a fainter grey
 * would give up the contrast [SECONDARY_INK_ALPHA] exists to hold, so the
 * weight carries the difference instead.
 */
private val TIME_SPAN = SpanStyle(fontWeight = FontWeight.Normal, letterSpacing = 0.sp)

/** A chip's text — its [time], set apart in [timeInk], before the [title]. */
internal fun monthChipLabel(time: String?, title: String, timeInk: Color): AnnotatedString =
    buildAnnotatedString {
        if (time != null) {
            withStyle(TIME_SPAN.copy(color = timeInk)) { append(time) }
            append(" ")
        }
        append(title)
    }

/**
 * The start time a chip shows before its title (#219), or null when it has none
 * to show: an all-day chip never carries a time, and neither does a bar carried
 * in from an earlier week, whose start is not in this segment.
 */
internal fun monthChipTime(
    event: EventInstance,
    continuesLeft: Boolean,
    zone: TimeZone,
    is24Hour: Boolean,
    locale: Locale,
): String? {
    if (event.isAllDay || continuesLeft) return null
    val start = event.start.toLocalDateTime(zone).time
    return formatTimeOfDay(start.hour, start.minute, is24Hour, locale)
}

/**
 * The narrowest chip that may show a time (#219): wide enough for the widest
 * time in the current convention plus [TITLE_SAMPLE]'s worth of title.
 *
 * Measured rather than a device breakpoint, so it follows the font scale, the
 * 12/24-hour setting, the locale's own time format and the week-number gutter
 * on its own — landscape, an unfolded foldable and a tablet all come out wide
 * enough without any of them being named.
 */
@Composable
internal fun rememberMonthTimeChipWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall
    val density = LocalDensity.current
    val locale = currentLocale()
    val sample = formatTimeOfDay(SAMPLE_HOUR, SAMPLE_MINUTE, LocalUse24HourFormat.current, locale)
    return remember(sample, style, density, measurer) {
        val label = monthChipLabel(sample, TITLE_SAMPLE, Color.Unspecified)
        val textPx = measurer.measure(label, style).size.width
        with(density) { textPx.toDp() } + CHIP_CHROME
    }
}
