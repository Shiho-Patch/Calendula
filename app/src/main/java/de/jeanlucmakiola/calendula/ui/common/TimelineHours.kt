package de.jeanlucmakiola.calendula.ui.common

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The hours the week and day timelines fit to the screen when they open (#322).
 *
 * Nothing outside the range is hidden: [TimelineScale.FitHours] only sizes the
 * hour so that [startHour]..[endHour] fills the viewport, and the timeline opens
 * scrolled to [startHour]. The hours are kept when [enabled] is switched off, so
 * switching it back on restores the chosen range.
 */
data class TimelineHours(
    val enabled: Boolean = false,
    val startHour: Int = DEFAULT_START_HOUR,
    val endHour: Int = DEFAULT_END_HOUR,
) {
    val span: Int get() = endHour - startHour

    companion object {
        const val DEFAULT_START_HOUR = 7
        const val DEFAULT_END_HOUR = 22

        /** Shortest range that can be chosen, in hours. */
        const val MIN_SPAN = 2

        /**
         * A range with [startHour] and [endHour] held to the day and at least
         * [MIN_SPAN] apart; the start gives way when the two collide.
         */
        fun of(enabled: Boolean, startHour: Int, endHour: Int): TimelineHours {
            val end = endHour.coerceIn(MIN_SPAN, 24)
            val start = startHour.coerceIn(0, end - MIN_SPAN)
            return TimelineHours(enabled, start, end)
        }

        /** Start hours that still leave [MIN_SPAN] before [endHour]. */
        fun startOptions(endHour: Int): List<Int> = (0..endHour - MIN_SPAN).toList()

        /** End hours at least [MIN_SPAN] after [startHour]; 24 is midnight. */
        fun endOptions(startHour: Int): List<Int> = (startHour + MIN_SPAN..24).toList()
    }
}

/**
 * The scroll offset that puts [startHour] at the top of a timeline laid out
 * with [maxValue] scroll range over a [viewportPx] viewport, or null while that
 * layout is not yet at [hourPx] per hour — the scale changes a frame before the
 * content grows to match it, and scrolling against the old layout would land on
 * the wrong hour.
 */
internal fun startHourScroll(maxValue: Int, viewportPx: Int, startHour: Int, hourPx: Float): Int? {
    if (viewportPx <= 0 || maxValue < 0 || maxValue == Int.MAX_VALUE) return null
    val laidOutHourPx = (maxValue + viewportPx) / 24f
    if (abs(laidOutHourPx - hourPx) > 1f) return null
    return (startHour * laidOutHourPx).roundToInt().coerceIn(0, maxValue)
}
