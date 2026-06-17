package de.jeanlucmakiola.calendula.ui

/**
 * A navigation a home-screen widget asked the app to perform when launched.
 * Parsed from the launch intent in MainActivity and consumed once by
 * [CalendarHost] (event taps reuse the existing reminder detail-key channel, so
 * they are not modelled here).
 */
sealed interface WidgetNavRequest {
    /** Open the day view anchored on [dateIso] (an ISO `yyyy-MM-dd` date). */
    data class OpenDate(val dateIso: String) : WidgetNavRequest

    /** Open the create-event form prefilled for [dateIso] (today when null). */
    data class Create(val dateIso: String?) : WidgetNavRequest
}
