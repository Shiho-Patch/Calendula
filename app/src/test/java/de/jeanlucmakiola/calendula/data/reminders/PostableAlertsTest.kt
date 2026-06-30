package de.jeanlucmakiola.calendula.data.reminders

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PostableAlertsTest {

    private fun alert(alertId: Long, calendarId: Long) = ReminderAlert(
        alertId = alertId,
        eventId = alertId * 10,
        calendarId = calendarId,
        beginMillis = 0L,
        endMillis = 0L,
        title = "Event $alertId",
        location = null,
        isAllDay = false,
    )

    @Test
    fun `keeps alerts when no calendar is disabled`() {
        val due = listOf(alert(1, calendarId = 100), alert(2, calendarId = 200))

        val postable = postableAlerts(due, disabledCalendarIds = emptySet())

        assertThat(postable).isEqualTo(due)
    }

    @Test
    fun `drops alerts for a disabled calendar`() {
        val keep = alert(1, calendarId = 100)
        val drop = alert(2, calendarId = 200)

        val postable = postableAlerts(listOf(keep, drop), disabledCalendarIds = setOf(200))

        assertThat(postable).containsExactly(keep)
    }

    @Test
    fun `drops every alert when all their calendars are disabled`() {
        val due = listOf(alert(1, calendarId = 100), alert(2, calendarId = 100))

        val postable = postableAlerts(due, disabledCalendarIds = setOf(100))

        assertThat(postable).isEmpty()
    }

    @Test
    fun `keeps multiple alerts from the same enabled calendar`() {
        val due = listOf(alert(1, calendarId = 100), alert(2, calendarId = 100))

        val postable = postableAlerts(due, disabledCalendarIds = setOf(999))

        assertThat(postable).isEqualTo(due)
    }
}
