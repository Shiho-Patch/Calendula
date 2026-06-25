package de.jeanlucmakiola.calendula.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * User input for creating an event (and, from v1.3, editing one). Times are
 * wall-clock values in the device zone; the data layer translates them to
 * provider millis (all-day events normalise to UTC midnights there).
 */
data class EventForm(
    val calendarId: Long?,
    val title: String = "",
    val isAllDay: Boolean = false,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val location: String = "",
    val description: String = "",
    /** Reminder lead times in minutes before the start, deduplicated. */
    val reminders: List<Int> = emptyList(),
    val availability: Availability = Availability.Busy,
    val accessLevel: AccessLevel = AccessLevel.Default,
    /**
     * Bare RRULE value (`Events.RRULE` convention, no "RRULE:" prefix); null
     * means a one-off event. May hold rules the simple picker can't express —
     * those are kept verbatim until the user picks something else.
     */
    val rrule: String? = null,
    /**
     * The event's own colour, or null to inherit the calendar's colour.
     * [colorKey] is a `Colors.COLOR_KEY` from the calendar account's published
     * event palette (Google, some CalDAV) — written as `EVENT_COLOR_KEY` so it
     * round-trips through sync. When it is null but [color] is set, [color] is
     * a raw ARGB written as `EVENT_COLOR` (local calendars, or synced ones the
     * user opted into despite no palette). [color] mirrors the key's swatch when
     * [colorKey] is set, so the picker can highlight it.
     */
    val colorKey: String? = null,
    val color: Int? = null,
    /**
     * Guests the user has added/kept on the event. Calendula only writes these
     * `Attendees` rows — it has no INTERNET and never sends an invitation
     * itself; whether a guest is notified is decided downstream by the
     * calendar's backend (local: no one; CalDAV/Google: the server/account).
     * Read-only rows the form doesn't model (the organizer, resources) are
     * preserved by the data layer, not carried here.
     */
    val attendees: List<EventAttendee> = emptyList(),
)

/**
 * One editable guest: the user controls the [email] (the identity we dedup and
 * write on), an optional display [name], and whether they're [optional] rather
 * than required. Response status and the organizer/resource distinction are not
 * user-editable, so they aren't modelled here.
 */
data class EventAttendee(
    val email: String,
    val name: String = "",
    val optional: Boolean = false,
)

/**
 * The form's optional sections. Which ones show by default is a user setting;
 * the rest unfold behind a "more fields" button.
 */
enum class EventFormField {
    Location,
    Description,
    Reminders,
    Recurrence,
    Availability,
    Visibility,
    Color,
    Attendees,
}

enum class EventFormProblem {
    /** No target calendar — none picked and no writable calendar exists. */
    NoCalendar,
    EndBeforeStart,
    /** The recurrence's UNTIL date lies before the event's first day. */
    RecurrenceEndsBeforeStart,
}

/**
 * Validation; an empty set means the form can be saved. A blank title is
 * allowed (display falls back to "(No title)", matching the provider), and a
 * zero-length timed event is allowed (spec §8: instant events exist).
 */
/**
 * Prefill the edit form from a loaded event. [beginMillis]/[endMillis] are the
 * tapped occurrence's own times (`Instances.BEGIN`/`END`), not the series
 * start — the data layer later turns a time edit into a delta on the series.
 *
 * All-day provider times are UTC midnights with an exclusive end; the form
 * shows the last covered day and keeps placeholder wall-clock times in case
 * the user switches the event to timed.
 */
fun EventDetail.toEditForm(beginMillis: Long, endMillis: Long, zone: TimeZone): EventForm {
    val (start, end) = if (instance.isAllDay) {
        val startDate = Instant.fromEpochMilliseconds(beginMillis)
            .toLocalDateTime(TimeZone.UTC).date
        val endExclusive = Instant.fromEpochMilliseconds(endMillis)
            .toLocalDateTime(TimeZone.UTC).date
        val endDate = maxOf(startDate, LocalDate.fromEpochDays(endExclusive.toEpochDays() - 1))
        LocalDateTime(startDate, LocalTime(9, 0)) to LocalDateTime(endDate, LocalTime(10, 0))
    } else {
        Instant.fromEpochMilliseconds(beginMillis).toLocalDateTime(zone) to
            Instant.fromEpochMilliseconds(endMillis).toLocalDateTime(zone)
    }
    return EventForm(
        calendarId = instance.calendarId,
        title = instance.title,
        isAllDay = instance.isAllDay,
        start = start,
        end = end,
        location = instance.location.orEmpty(),
        description = description.orEmpty(),
        reminders = reminders.map { it.minutes }.distinct().sorted(),
        availability = availability,
        accessLevel = accessLevel,
        rrule = rrule?.removePrefix("RRULE:")?.takeIf { it.isNotBlank() },
        // The provider fills EVENT_COLOR from the key, so [color] is the
        // swatch either way; a null colour means the event inherits its
        // calendar's colour.
        colorKey = eventColorKey,
        color = eventColor,
        // Only editable guests ride in the form: drop the organizer and
        // resource rows (not user-editable) and any without an email (we key
        // edits and dedup on the address). The data layer preserves the rows we
        // don't carry here, so they're never clobbered on save.
        attendees = attendees
            .filter {
                it.relationship != AttendeeRelationship.Organizer &&
                    it.type != AttendeeType.Resource
            }
            .mapNotNull { a ->
                a.email?.takeIf { it.isNotBlank() }?.let { email ->
                    EventAttendee(
                        email = email,
                        name = a.name,
                        optional = a.type == AttendeeType.Optional,
                    )
                }
            },
    )
}

/**
 * What the edit form saw when it loaded — compared against a fresh read at
 * save time to detect external changes (sync, another device) that landed
 * while the form was open. The raw row times ride along because
 * [toEditForm] derives the form's times from the *tapped occurrence*, so
 * re-deriving with the same occurrence would mask an externally moved
 * event. Guests are covered (the form writes editable attendees), so an
 * external attendee change now also trips the conflict check. Still not
 * covered: status, the user's own response, reminder methods, the
 * organizer/resource rows, and a recurring event's duration.
 */
data class EditSnapshot(
    val form: EventForm,
    /** The raw Events-row times (for recurring events: the series anchor). */
    val rowStart: Instant,
    val rowEnd: Instant,
)

fun EventDetail.toEditSnapshot(beginMillis: Long, endMillis: Long, zone: TimeZone): EditSnapshot =
    EditSnapshot(
        form = toEditForm(beginMillis, endMillis, zone),
        rowStart = instance.start,
        rowEnd = instance.end,
    )

/**
 * The optional sections that hold a value in [form] — when editing, these
 * must be visible regardless of the user's default-fields setting, or the
 * data they carry would be invisible (though still preserved).
 */
fun EventForm.populatedFields(): Set<EventFormField> = buildSet {
    if (location.isNotBlank()) add(EventFormField.Location)
    if (description.isNotBlank()) add(EventFormField.Description)
    if (reminders.isNotEmpty()) add(EventFormField.Reminders)
    if (rrule != null) add(EventFormField.Recurrence)
    if (availability != Availability.Busy) add(EventFormField.Availability)
    if (accessLevel != AccessLevel.Default) add(EventFormField.Visibility)
    if (colorKey != null || color != null) add(EventFormField.Color)
    if (attendees.isNotEmpty()) add(EventFormField.Attendees)
}

fun EventForm.problems(): Set<EventFormProblem> = buildSet {
    if (calendarId == null) add(EventFormProblem.NoCalendar)
    val endsTooEarly = if (isAllDay) end.date < start.date else end < start
    if (endsTooEarly) add(EventFormProblem.EndBeforeStart)
    // An UNTIL before the first day would make the provider generate zero
    // occurrences — the event would silently vanish from every view.
    val recurrenceEnd = rrule?.let(::parseSimpleRecurrence)?.end
    if (recurrenceEnd is RecurrenceEnd.Until && recurrenceEnd.date < start.date) {
        add(EventFormProblem.RecurrenceEndsBeforeStart)
    }
}
