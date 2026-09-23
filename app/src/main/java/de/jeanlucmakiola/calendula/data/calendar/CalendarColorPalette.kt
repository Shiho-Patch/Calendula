package de.jeanlucmakiola.calendula.data.calendar

/**
 * The colours offered when creating or editing a calendar; ARGB ints for a raw
 * `CALENDAR_COLOR` / `EVENT_COLOR`.
 *
 * Hues rather than final fills: every surface that draws a calendar colour runs
 * it through the app's own tone pass first, so these are chosen to stay
 * distinguishable after it. Shared with Agendula, whose list palette these are —
 * the two apps are the same family and a calendar and a task list picked from
 * different-looking sets read as different products.
 *
 * The named entries exist for callers that need one specific hue (the managed
 * special-dates calendars), so they can't drift from the swatches on offer.
 */
object CalendarColorPalette {
    val Mauve = 0xFF7A5C6B.toInt()
    val Red = 0xFFD7484A.toInt()
    val Orange = 0xFFE8743B.toInt()
    val Amber = 0xFFE0A32E.toInt()
    val Olive = 0xFF7CA83E.toInt()
    val Green = 0xFF35A06A.toInt()
    val Teal = 0xFF19938C.toInt()
    val Cyan = 0xFF2A9BC4.toInt()
    val Blue = 0xFF3C74C8.toInt()
    val Indigo = 0xFF6A5CC0.toInt()
    val Purple = 0xFF9455B8.toInt()
    val Pink = 0xFFC94F8E.toInt()

    /** The full palette, in swatch-grid order. */
    val all: List<Int> = listOf(
        Mauve, Red, Orange, Amber, Olive, Green,
        Teal, Cyan, Blue, Indigo, Purple, Pink,
    )
}
