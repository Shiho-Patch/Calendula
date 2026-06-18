package de.jeanlucmakiola.calendula.domain.ics

import de.jeanlucmakiola.calendula.domain.Availability
import de.jeanlucmakiola.calendula.domain.EventStatus
import kotlin.time.Instant

/**
 * A single event ready to be serialised to a `VEVENT`, decoupled from the
 * provider so [IcsWriter] stays pure-Kotlin and JVM-testable. [start]/[end] are
 * absolute instants; [isAllDay] and [recurrenceRule] decide how they are
 * rendered (see [IcsWriter]'s timezone rule).
 */
data class IcsEvent(
    /** RFC 5545 UID — globally stable across exports (see [deriveIcsUid]). */
    val uid: String,
    val summary: String,
    val start: Instant,
    /** Exclusive end (for all-day: the next-day midnight, as the provider stores it). */
    val end: Instant,
    val isAllDay: Boolean,
    /** IANA zone id (`Events.EVENT_TIMEZONE`); only used for recurring timed events. */
    val zoneId: String,
    /** Bare RRULE value (no `RRULE:` prefix), or null for a one-off event. */
    val recurrenceRule: String? = null,
    val location: String? = null,
    val description: String? = null,
    /** Reminder lead times in minutes before start (raw provider offsets). */
    val reminderMinutes: List<Int> = emptyList(),
    val status: EventStatus = EventStatus.Confirmed,
    val availability: Availability = Availability.Busy,
    /** Source calendar name, emitted as `X-CALENDULA-CALENDAR` so a combined backup can fan back out. */
    val calendarName: String? = null,
)

/**
 * The UID to export for a provider event. A row that already carries a UID
 * (`Events.UID_2445`) keeps it; otherwise we synthesise a **stable** one from
 * the event id and its DTSTART so the same legacy event yields the same UID
 * across repeated backups — which keeps a later restore from duplicating it.
 */
fun deriveIcsUid(existingUid: String?, eventId: Long, dtStartMillis: Long): String =
    existingUid?.trim()?.takeIf { it.isNotEmpty() }
        ?: "$eventId-$dtStartMillis@calendula"
