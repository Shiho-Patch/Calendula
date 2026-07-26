package de.jeanlucmakiola.calendula.data.reminders

import de.jeanlucmakiola.calendula.domain.reminders.PlannedReminder

/**
 * One reminder as the notification layer needs it: what to show, and the stable
 * [key] that identifies it across a reboot, a re-scan and a reinstall.
 *
 * [key] used to be the `CalendarAlerts` row id. It is now derived from the
 * reminder itself (see [PlannedReminder.key]) because there is no row any more —
 * in-house delivery reads `Instances` and `Reminders` and owns the alarm (#75).
 * Everything downstream only ever needed it to be stable and unique, which it
 * still is: it keys the notification tag, so a reminder posted twice replaces
 * itself instead of stacking, and it keys the snooze/dismiss `PendingIntent`s.
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
