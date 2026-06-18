package de.jeanlucmakiola.calendula.domain.ics

import de.jeanlucmakiola.calendula.domain.EventDetail

/**
 * Build the [IcsEvent] for sharing a single event. We export the event the user
 * is looking at as a **one-off** VEVENT (no RRULE): the detail screen shows one
 * occurrence, so "share this event" should hand off exactly that instance, not
 * a whole series anchored to a possibly-different DTSTART. Reminders are the
 * already-decoded semantic lead times the detail screen holds.
 */
fun EventDetail.toShareIcsEvent(): IcsEvent {
    val startMillis = instance.start.toEpochMilliseconds()
    return IcsEvent(
        uid = deriveIcsUid(existingUid = null, eventId = instance.eventId, dtStartMillis = startMillis),
        summary = instance.title,
        start = instance.start,
        end = instance.end,
        isAllDay = instance.isAllDay,
        zoneId = eventTimezone?.takeIf { it.isNotBlank() } ?: "UTC",
        recurrenceRule = null,
        location = instance.location,
        description = description,
        reminderMinutes = reminders.map { it.minutes },
        status = status,
        availability = availability,
        calendarName = null,
    )
}
