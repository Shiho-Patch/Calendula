package de.jeanlucmakiola.calendula.data.reminders

import de.jeanlucmakiola.calendula.domain.reminders.PlannedReminder

/**
 * One reminder as the notification layer needs it: what to show, and the stable
 * [key] identifying it across a reboot, a re-scan and a reinstall. Derived from
 * the reminder itself (see [PlannedReminder.key]) — in-house delivery has no
 * `CalendarAlerts` row to take an id from (#75).
 */
data class ReminderAlert(
    val key: Long,
    val eventId: Long,
    val calendarId: Long,
    val beginMillis: Long,
    val endMillis: Long,
    /** Raw event title; may be blank — the notifier substitutes "(no title)". */
    val title: String,
    val location: String?,
    val isAllDay: Boolean,
)

fun PlannedReminder.toAlert(): ReminderAlert = ReminderAlert(
    key = key,
    eventId = instance.eventId,
    calendarId = instance.calendarId,
    beginMillis = instance.beginMillis,
    endMillis = instance.endMillis,
    title = instance.title,
    location = instance.location,
    isAllDay = instance.isAllDay,
)
