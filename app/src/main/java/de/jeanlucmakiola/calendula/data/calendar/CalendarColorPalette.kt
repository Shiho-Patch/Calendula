package de.jeanlucmakiola.calendula.data.calendar

/**
 * Google-Calendar-style palette; ARGB ints for a raw `CALENDAR_COLOR` /
 * `EVENT_COLOR`. The named entries exist for callers that need one specific
 * hue (the managed special-dates calendars), so they can't drift from the
 * swatches offered in the colour picker.
 */
object CalendarColorPalette {
    val Red = 0xFFD50000.toInt()
    val Orange = 0xFFE67C00.toInt()
    val Amber = 0xFFF6BF26.toInt()
    val Green = 0xFF33B679.toInt()
    val DarkGreen = 0xFF0B8043.toInt()
    val Blue = 0xFF039BE5.toInt()
    val Indigo = 0xFF3F51B5.toInt()
    val Purple = 0xFF8E24AA.toInt()
    val Graphite = 0xFF616161.toInt()

    /** The full palette, in swatch-row order. */
    val all: List<Int> = listOf(Red, Orange, Amber, Green, DarkGreen, Blue, Indigo, Purple, Graphite)
}
