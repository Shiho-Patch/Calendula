package de.jeanlucmakiola.calendula.ui.month

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.ui.common.formatTimeOfDay
import de.jeanlucmakiola.calendula.ui.common.rememberInlineTimeWidth
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

/** The narrowest chip that may show a time — the text it needs, plus its chrome. */
@Composable
internal fun rememberMonthTimeChipWidth(): Dp =
    rememberInlineTimeWidth(MaterialTheme.typography.labelSmall) + CHIP_CHROME
