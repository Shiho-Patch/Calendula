package de.jeanlucmakiola.calendula.ui

import de.jeanlucmakiola.calendula.ui.common.CalendarView

/**
 * A navigation a home-screen widget asked the app to perform when launched.
 * Parsed from the launch intent in MainActivity and consumed once by
 * [CalendarHost]. Every request carries the [source] view of the widget it came
 * from (the agenda widget → [CalendarView.Agenda], the month widget →
 * [CalendarView.Month]) so the in-app back stack roots itself in that view:
 * backing out of the opened date/event returns to the widget's own view, not the
 * default home. (Reminder notifications are not widgets — they keep the separate
 * detail-key channel and leave the base view untouched.)
 */
sealed interface WidgetNavRequest {
    /**
     * Open the day view anchored on [dateIso] (an ISO `yyyy-MM-dd` date), over
     * [source]. A null [source] means the request came from outside the app (a
     * launcher/clock date tap, issue #9) rather than a widget, so it roots over
     * the default home view instead of a widget's view.
     */
    data class OpenDate(val dateIso: String, val source: CalendarView?) : WidgetNavRequest

    /** Open one occurrence's detail (an agenda-widget event tap), over [source]. */
    data class OpenEvent(
        val eventId: Long,
        val beginMillis: Long,
        val endMillis: Long,
        val source: CalendarView,
    ) : WidgetNavRequest

    /** Open the create-event form prefilled for [dateIso] (today when null). */
    data class Create(val dateIso: String?) : WidgetNavRequest
}
