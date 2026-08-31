package de.jeanlucmakiola.calendula.data.calendar

import android.provider.CalendarContract
import de.jeanlucmakiola.calendula.domain.AccessLevel
import de.jeanlucmakiola.calendula.domain.Availability
import de.jeanlucmakiola.calendula.domain.EventColorOption
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventStatus
import de.jeanlucmakiola.calendula.domain.ics.ParsedIcsEvent
import de.jeanlucmakiola.calendula.domain.nearestTo
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.LocalDateTime as JavaLocalDateTime

/** Provider-ready DTSTART / DTEND / EVENT_TIMEZONE for an event write. */
internal data class EventWriteTimes(
    val dtStartMillis: Long,
    val dtEndMillis: Long,
    val timezone: String,
)

/**
 * All-day events live at UTC midnights with an exclusive DTEND (the
 * CalendarContract convention — a one-day event ends at the next midnight);
 * timed events resolve their wall-clock values in the form's own
 * [EventForm.timezone], falling back to [zone] (the device) when it doesn't pin
 * one. Passing the device zone is therefore still correct for an unpinned form —
 * but it no longer overrides a pinned event's zone, which is what used to
 * silently re-anchor a foreign-zone event to the device on any time edit.
 */
internal fun EventForm.toWriteTimes(zone: ZoneId): EventWriteTimes = if (isAllDay) {
    EventWriteTimes(
        dtStartMillis = start.date.toJavaLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        dtEndMillis = end.date.toJavaLocalDate().plusDays(1)
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        timezone = "UTC",
    )
} else {
    val writeZone = writeZone(zone)
    EventWriteTimes(
        dtStartMillis = start.toJavaLocalDateTime().atZone(writeZone).toInstant().toEpochMilli(),
        dtEndMillis = end.toJavaLocalDateTime().atZone(writeZone).toInstant().toEpochMilli(),
        timezone = writeZone.id,
    )
}

/**
 * The zone this form's DTSTART is expressed in: UTC for an all-day event (the
 * provider's date anchor), otherwise the form's own pinned zone, falling back to
 * [deviceZone] when it doesn't pin one or pins something the tz database can't
 * parse.
 */
private fun EventForm.writeZone(deviceZone: ZoneId): ZoneId = if (isAllDay) {
    ZoneOffset.UTC
} else {
    timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: deviceZone
}

/**
 * The form's start as a bare wall-clock value — what the user sees on the form,
 * stripped of any zone. All-day events use their date's midnight rather than
 * [EventForm.start]'s placeholder time-of-day, which exists only so switching the
 * event back to timed has something to show.
 */
private fun EventForm.anchorLocal(): JavaLocalDateTime = if (isAllDay) {
    start.date.toJavaLocalDate().atStartOfDay()
} else {
    start.toJavaLocalDateTime()
}

/**
 * RFC 2445 duration for a recurring event's row (the provider requires
 * DURATION instead of DTEND when an RRULE is set): whole days for all-day
 * events, seconds otherwise.
 */
internal fun EventWriteTimes.toRfc2445Duration(isAllDay: Boolean): String =
    rfc2445Duration(dtEndMillis - dtStartMillis, isAllDay)

/**
 * RFC 2445 duration for a [spanMillis]-long event: whole days for all-day
 * events (the provider's convention), seconds otherwise. Shared by the write
 * paths that need a DURATION but start from a raw millisecond span (the series
 * copy and exception replay of a calendar move) rather than [EventWriteTimes].
 */
internal fun rfc2445Duration(spanMillis: Long, isAllDay: Boolean): String = if (isAllDay) {
    "P${spanMillis / MILLIS_PER_DAY}D"
} else {
    "P${spanMillis / 1_000L}S"
}

/**
 * Column values for a brand-new Events row: identity, times (the provider's
 * invariant — recurring rows carry RRULE+DURATION and no DTEND, one-off rows
 * carry DTEND), availability/access level and trimmed optional text. Shared by
 * the user-event and managed-event insert paths, which differ only in the
 * [uid] they stamp; colour and attendees are user-event concerns the caller
 * layers on top.
 */
internal fun buildEventInsertValues(
    form: EventForm,
    uid: String,
    times: EventWriteTimes,
): Map<String, Any?> = buildMap {
    put(
        CalendarContract.Events.CALENDAR_ID,
        requireNotNull(form.calendarId) { "EventForm.calendarId is required" },
    )
    put(CalendarContract.Events.UID_2445, uid)
    put(CalendarContract.Events.TITLE, form.title.trim())
    put(CalendarContract.Events.ALL_DAY, if (form.isAllDay) 1 else 0)
    put(CalendarContract.Events.DTSTART, times.dtStartMillis)
    if (form.rrule == null) {
        put(CalendarContract.Events.DTEND, times.dtEndMillis)
    } else {
        put(CalendarContract.Events.RRULE, form.rrule)
        put(CalendarContract.Events.DURATION, times.toRfc2445Duration(form.isAllDay))
    }
    put(CalendarContract.Events.EVENT_TIMEZONE, times.timezone)
    put(CalendarContract.Events.AVAILABILITY, form.availability.toProviderValue())
    put(CalendarContract.Events.ACCESS_LEVEL, form.accessLevel.toProviderValue())
    form.location.trim().takeIf { it.isNotEmpty() }
        ?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
    form.description.trim().takeIf { it.isNotEmpty() }
        ?.let { put(CalendarContract.Events.DESCRIPTION, it) }
}

/**
 * Dirty-checked column values for updating an existing Events row: only what
 * the user actually changed is written, so untouched fields can't stomp
 * concurrent external edits. Keys are `CalendarContract.Events` columns; a
 * null value means "set the column to NULL". An empty map means nothing on
 * the row changed.
 *
 * Time fields travel together (the provider validates them as a unit):
 * - unchanged times, all-day flag and rrule → no time columns at all;
 * - non-recurring result → DTSTART/DTEND, DURATION and RRULE cleared;
 * - recurring result → the *series* DTSTART moves by the same **wall-clock**
 *   shift the user applied to the displayed occurrence and is re-resolved in the
 *   event's zone ([seriesDtStartMillis] is the row's current DTSTART), DURATION
 *   replaces DTEND, RRULE is written. This keeps past occurrences intact when
 *   someone edits a later occurrence's time, and keeps the anchor's time-of-day
 *   stable across a DST boundary or a zone change between the two.
 */
internal fun buildEventUpdateValues(
    original: EventForm,
    updated: EventForm,
    seriesDtStartMillis: Long,
    zone: ZoneId,
): Map<String, Any?> = buildMap {
    if (updated.title.trim() != original.title.trim()) {
        put(CalendarContract.Events.TITLE, updated.title.trim())
    }
    if (updated.location.trim() != original.location.trim()) {
        put(CalendarContract.Events.EVENT_LOCATION, updated.location.trim().ifEmpty { null })
    }
    if (updated.description.trim() != original.description.trim()) {
        put(CalendarContract.Events.DESCRIPTION, updated.description.trim().ifEmpty { null })
    }
    if (updated.availability != original.availability) {
        put(CalendarContract.Events.AVAILABILITY, updated.availability.toProviderValue())
    }
    if (updated.accessLevel != original.accessLevel) {
        put(CalendarContract.Events.ACCESS_LEVEL, updated.accessLevel.toProviderValue())
    }
    if (updated.colorKey != original.colorKey || updated.color != original.color) {
        putAll(eventColorColumns(updated.colorKey, updated.color))
    }

    // A zone change counts as a time change even when the wall-clock is
    // untouched: the same 09:00 in another zone is a different instant, so
    // DTSTART has to move with it.
    val timesChanged = updated.start != original.start ||
        updated.end != original.end ||
        updated.isAllDay != original.isAllDay ||
        updated.rrule != original.rrule ||
        updated.timezone != original.timezone
    if (!timesChanged) return@buildMap

    val newTimes = updated.toWriteTimes(zone)
    put(CalendarContract.Events.ALL_DAY, if (updated.isAllDay) 1 else 0)
    put(CalendarContract.Events.EVENT_TIMEZONE, newTimes.timezone)
    if (updated.rrule == null) {
        put(CalendarContract.Events.DTSTART, newTimes.dtStartMillis)
        put(CalendarContract.Events.DTEND, newTimes.dtEndMillis)
        put(CalendarContract.Events.RRULE, null)
        put(CalendarContract.Events.DURATION, null)
    } else {
        // Move the series anchor by the *wall-clock* shift the user applied to the
        // displayed occurrence, then re-resolve it in the event's (possibly new)
        // zone — never by a millisecond delta. An instant delta silently bakes in
        // the offset that happened to apply on the edited occurrence's date, which
        // is a different offset from the series anchor's whenever a DST boundary
        // sits between them, or whenever the zone itself changed. Working in wall
        // clock keeps "09:00" meaning 09:00 at both ends.
        val seriesLocal = Instant.ofEpochMilli(seriesDtStartMillis)
            .atZone(original.writeZone(zone)).toLocalDateTime()
        val wallClockShift = Duration.between(original.anchorLocal(), updated.anchorLocal())
        val shifted = seriesLocal.plus(wallClockShift)
        // An all-day series anchor must sit on a UTC midnight. A pure day move
        // already lands there (both ends are midnights), but *switching* a
        // recurring event to all-day shifts by a time-of-day too, so snap.
        val newSeriesLocal = if (updated.isAllDay) shifted.toLocalDate().atStartOfDay() else shifted
        val newSeriesStart = newSeriesLocal.atZone(updated.writeZone(zone))
        put(CalendarContract.Events.DTSTART, newSeriesStart.toInstant().toEpochMilli())
        put(CalendarContract.Events.DTEND, null)
        put(CalendarContract.Events.RRULE, updated.rrule)
        put(CalendarContract.Events.DURATION, newTimes.toRfc2445Duration(updated.isAllDay))
    }
}

/**
 * Column values for a modified-occurrence exception row ("edit only this
 * event"): inserting them at `Events.CONTENT_EXCEPTION_URI/<id>` makes the
 * provider clone the series row and apply these on top. Unlike the series
 * update there is no dirty check — the exception is a fresh row, so every
 * form-backed column is written (empty optionals as explicit NULLs, since the
 * clone starts from the parent's values).
 *
 * The occurrence's length travels as DURATION, never DTEND: the provider
 * rejects DTEND on an exception outright (`CalendarProvider2`:
 * "Exceptions can't overwrite dtend") and derives the instance end from
 * DTSTART + DURATION itself, clearing the inherited RRULE in the process. This
 * matches how AOSP Calendar/Etar write exceptions; sending DTEND is what made
 * "only this event" fail on-device (Codeberg #16).
 */
internal fun buildOccurrenceExceptionValues(
    form: EventForm,
    originalInstanceMillis: Long,
    zone: ZoneId,
): Map<String, Any?> = buildMap {
    val times = form.toWriteTimes(zone)
    put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, originalInstanceMillis)
    put(CalendarContract.Events.TITLE, form.title.trim())
    put(CalendarContract.Events.ALL_DAY, if (form.isAllDay) 1 else 0)
    put(CalendarContract.Events.DTSTART, times.dtStartMillis)
    put(CalendarContract.Events.DURATION, times.toRfc2445Duration(form.isAllDay))
    put(CalendarContract.Events.EVENT_TIMEZONE, times.timezone)
    put(CalendarContract.Events.AVAILABILITY, form.availability.toProviderValue())
    put(CalendarContract.Events.ACCESS_LEVEL, form.accessLevel.toProviderValue())
    put(CalendarContract.Events.EVENT_LOCATION, form.location.trim().ifEmpty { null })
    put(CalendarContract.Events.DESCRIPTION, form.description.trim().ifEmpty { null })
    putAll(eventColorColumns(form.colorKey, form.color))
}

/**
 * The form as a **detached occurrence**: the same edited values, with the
 * series rule dropped so [buildEventInsertValues] writes a standalone one-off
 * row (DTSTART + DTEND, no RRULE/DURATION) at the occurrence's own times.
 *
 * The "edit only this event" shape for a series with **no `_sync_id`**, where an
 * exception row can't attach to its parent at all (Codeberg #234).
 *
 * The exception path gets the rule dropped for free — the provider clears the
 * RRULE it cloned when an exception carries DTSTART + DURATION
 * ([buildOccurrenceExceptionValues]). Here nothing is cloned, so it is stripped
 * by hand; leaving it on would insert a second *series* overlapping the first.
 */
internal fun EventForm.toDetachedOccurrence(): EventForm = copy(rrule = null)

/**
 * Raw provider snapshot of a master/one-off Events row, enough to re-insert it
 * verbatim on another calendar (a calendar move is copy+delete — `CALENDAR_ID`
 * is sync-adapter-owned and can't be updated in place). Recurring rows carry
 * [rrule]/[duration] (and any [rdate]/[exdate]) with a null [dtEndMillis];
 * one-off rows carry [dtEndMillis]. Colour is deliberately absent: a raw
 * `EVENT_COLOR` or account-scoped `EVENT_COLOR_KEY` may be invalid on the target
 * account, so the moved copy inherits the target calendar's colour instead.
 */
internal data class MasterEventSnapshot(
    val title: String,
    val isAllDay: Boolean,
    val dtStartMillis: Long,
    val dtEndMillis: Long?,
    val duration: String?,
    val rrule: String?,
    val rdate: String?,
    val exdate: String?,
    val timezone: String?,
    val availability: Int,
    val accessLevel: Int,
    val status: Int?,
    val location: String?,
    val description: String?,
)

/**
 * Column values re-creating [snapshot] as a fresh Events row on
 * [targetCalendarId], keeping its [uid] so `.ics` backup dedup and sync identity
 * survive the move. Preserves the recurrence skeleton (DTSTART/RRULE/DURATION,
 * RDATE/EXDATE) so the series' generated instances — and therefore the
 * ORIGINAL_INSTANCE_TIME of every copied exception — line up unchanged. The
 * caller layers the user's field edits on top with a normal series update.
 */
internal fun buildMovedMasterValues(
    snapshot: MasterEventSnapshot,
    targetCalendarId: Long,
    uid: String,
): Map<String, Any?> = buildMap {
    put(CalendarContract.Events.CALENDAR_ID, targetCalendarId)
    put(CalendarContract.Events.UID_2445, uid)
    put(CalendarContract.Events.TITLE, snapshot.title)
    put(CalendarContract.Events.ALL_DAY, if (snapshot.isAllDay) 1 else 0)
    put(CalendarContract.Events.DTSTART, snapshot.dtStartMillis)
    put(CalendarContract.Events.EVENT_TIMEZONE, snapshot.timezone ?: "UTC")
    if (snapshot.rrule != null) {
        put(CalendarContract.Events.RRULE, snapshot.rrule)
        snapshot.rdate?.takeIf { it.isNotBlank() }?.let { put(CalendarContract.Events.RDATE, it) }
        snapshot.exdate?.takeIf { it.isNotBlank() }?.let { put(CalendarContract.Events.EXDATE, it) }
        put(CalendarContract.Events.DURATION, snapshot.movedDuration())
    } else {
        snapshot.dtEndMillis?.let { put(CalendarContract.Events.DTEND, it) }
    }
    put(CalendarContract.Events.AVAILABILITY, snapshot.availability)
    put(CalendarContract.Events.ACCESS_LEVEL, snapshot.accessLevel)
    snapshot.status?.let { put(CalendarContract.Events.STATUS, it) }
    put(CalendarContract.Events.EVENT_LOCATION, snapshot.location?.ifEmpty { null })
    put(CalendarContract.Events.DESCRIPTION, snapshot.description?.ifEmpty { null })
}

/** The recurring copy's DURATION: its own if present, else derived from DTEND. */
private fun MasterEventSnapshot.movedDuration(): String = duration?.takeIf { it.isNotBlank() }
    ?: rfc2445Duration((dtEndMillis ?: dtStartMillis) - dtStartMillis, isAllDay)

/**
 * Raw provider snapshot of one exception row of a recurring series (a modified
 * or cancelled occurrence, `ORIGINAL_ID` = the series). [originalInstanceMillis]
 * ties it to the occurrence it overrides; a [isCancelled] row only needs that.
 */
internal data class ExceptionRowSnapshot(
    val exceptionEventId: Long,
    val originalInstanceMillis: Long,
    val isCancelled: Boolean,
    val status: Int?,
    val title: String,
    val isAllDay: Boolean,
    val dtStartMillis: Long,
    val dtEndMillis: Long?,
    val duration: String?,
    val timezone: String?,
    val availability: Int,
    val accessLevel: Int,
    val location: String?,
    val description: String?,
)

/**
 * Column values replaying a *modified* occurrence [snapshot] against the moved
 * series via `CONTENT_EXCEPTION_URI`. Like [buildOccurrenceExceptionValues] the
 * length travels as DURATION (the provider rejects DTEND on an exception). A
 * cancelled occurrence is written separately (ORIGINAL_INSTANCE_TIME +
 * STATUS_CANCELED) — this builder is only for the modified case.
 */
internal fun buildCopiedExceptionValues(snapshot: ExceptionRowSnapshot): Map<String, Any?> =
    buildMap {
        put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, snapshot.originalInstanceMillis)
        put(CalendarContract.Events.TITLE, snapshot.title)
        put(CalendarContract.Events.ALL_DAY, if (snapshot.isAllDay) 1 else 0)
        put(CalendarContract.Events.DTSTART, snapshot.dtStartMillis)
        put(
            CalendarContract.Events.DURATION,
            snapshot.duration?.takeIf { it.isNotBlank() }
                ?: rfc2445Duration(
                    (snapshot.dtEndMillis ?: snapshot.dtStartMillis) - snapshot.dtStartMillis,
                    snapshot.isAllDay,
                ),
        )
        put(CalendarContract.Events.EVENT_TIMEZONE, snapshot.timezone ?: "UTC")
        put(CalendarContract.Events.AVAILABILITY, snapshot.availability)
        put(CalendarContract.Events.ACCESS_LEVEL, snapshot.accessLevel)
        put(CalendarContract.Events.EVENT_LOCATION, snapshot.location?.ifEmpty { null })
        put(CalendarContract.Events.DESCRIPTION, snapshot.description?.ifEmpty { null })
        snapshot.status?.let { put(CalendarContract.Events.STATUS, it) }
    }

/**
 * Column values for a *cancelled*-occurrence exception row ("delete only this
 * event"): inserting them at `Events.CONTENT_EXCEPTION_URI/<id>` makes the
 * provider clone the series row and cancel exactly this one instance.
 *
 * As with [buildOccurrenceExceptionValues], the occurrence must be anchored with
 * DTSTART + DURATION so the provider derives a single instance and clears the
 * inherited RRULE. A STATUS-only cancel skips that: the clone keeps the RRULE, so
 * the *whole series* is cancelled and every other occurrence disappears
 * (Codeberg #47). The occurrence's length/zone come straight from the series row
 * — cancelling never changes them.
 */
internal fun buildOccurrenceCancelValues(
    originalInstanceMillis: Long,
    dtStartMillis: Long,
    duration: String?,
    timezone: String?,
    allDay: Int,
): Map<String, Any?> = buildMap {
    put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, originalInstanceMillis)
    put(CalendarContract.Events.DTSTART, dtStartMillis)
    put(CalendarContract.Events.DURATION, duration)
    put(CalendarContract.Events.EVENT_TIMEZONE, timezone)
    put(CalendarContract.Events.ALL_DAY, allDay)
    put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
}

/**
 * The master-row columns that drop the occurrence at [occurrenceMillis] from a
 * series by adding it to `EXDATE` — the path for events that have **no
 * `_sync_id`** (a local calendar, or a synced event not yet pushed).
 *
 * A cancelled exception row (see [buildOccurrenceCancelValues]) only attaches to
 * its parent through `ORIGINAL_SYNC_ID`. Without a `_sync_id` the link never
 * forms, and the provider's expansion of the *parent* collapses — every other
 * occurrence disappears (Codeberg #47, reproduced on a local calendar). EXDATE
 * needs no link, and is the canonical iCalendar way to drop an occurrence, so a
 * sync adapter carries it upstream unchanged if the calendar later syncs.
 *
 * The whole time/recurrence set is rewritten alongside it on purpose. The
 * provider does **not** treat an EXDATE-only update as a recurrence change: it
 * leaves the expanded `Instances` rows untouched, so the occurrence stays visible
 * (and, symmetrically, un-excluding one leaves it hidden). Writing DTSTART with
 * it forces the re-expansion — but DTSTART *alone* makes the provider recompute
 * `lastDate` as if the event were a single instance, collapsing the series to its
 * first occurrence. Passing DTSTART + DURATION + RRULE + zone together is what
 * re-expands it correctly. All observed on a Pixel; see the #47 notes.
 *
 * EXDATE is a comma-separated list, so an existing one is appended to (a repeat
 * of the same occurrence is folded away). All-day series take the `VALUE=DATE`
 * form (`yyyyMMdd`), timed ones the UTC date-time form (`yyyyMMddTHHmmssZ`).
 */
internal fun buildOccurrenceExdateValues(
    existingExdate: String?,
    occurrenceMillis: Long,
    dtStartMillis: Long,
    rrule: String?,
    duration: String?,
    timezone: String?,
    allDay: Int,
): Map<String, Any?> {
    val stamp = formatExdateStamp(occurrenceMillis, isAllDay = allDay != 0)
    val existing = existingExdate?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        .orEmpty()
    val merged = (existing + stamp).distinct().joinToString(",")
    return mapOf(
        CalendarContract.Events.EXDATE to merged,
        CalendarContract.Events.DTSTART to dtStartMillis,
        CalendarContract.Events.RRULE to rrule,
        CalendarContract.Events.DURATION to duration,
        CalendarContract.Events.EVENT_TIMEZONE to timezone,
        CalendarContract.Events.ALL_DAY to allDay,
    )
}

/**
 * Whether [existingExdate] already excludes the occurrence at [occurrenceMillis]
 * — i.e. it has already been dropped from the series, deleted or detached.
 *
 * Guards the detach path against running twice from a stale screen: the EXDATE
 * merge folds the repeat away silently and the update still reports one row
 * changed, so a second save would leave a second standalone copy.
 */
internal fun exdateContains(
    existingExdate: String?,
    occurrenceMillis: Long,
    isAllDay: Boolean,
): Boolean {
    val stamp = formatExdateStamp(occurrenceMillis, isAllDay)
    return existingExdate?.split(',')?.any { it.trim() == stamp } == true
}

/**
 * One EXDATE entry for the occurrence starting at [occurrenceMillis]. Both forms
 * are UTC: the provider stores an all-day DTSTART at UTC midnight, so its date
 * reads off the UTC calendar day.
 */
private fun formatExdateStamp(occurrenceMillis: Long, isAllDay: Boolean): String {
    val utc = Instant.ofEpochMilli(occurrenceMillis).atZone(ZoneOffset.UTC)
    return if (isAllDay) {
        "%04d%02d%02d".format(utc.year, utc.monthValue, utc.dayOfMonth)
    } else {
        "%04d%02d%02dT%02d%02d%02dZ".format(
            utc.year, utc.monthValue, utc.dayOfMonth,
            utc.hour, utc.minute, utc.second,
        )
    }
}

/**
 * The `EVENT_COLOR` / `EVENT_COLOR_KEY` columns for a colour selection. A
 * [colorKey] writes the key alone (the provider derives `EVENT_COLOR` from the
 * account's palette, so the colour round-trips through sync); a raw [color]
 * writes `EVENT_COLOR` and clears any key; "no colour" clears both so the event
 * falls back to its calendar's colour. The two are never written together —
 * the provider rejects a raw colour on a calendar that publishes a palette,
 * which is exactly why palette calendars only ever go through the key.
 */
internal fun eventColorColumns(colorKey: String?, color: Int?): Map<String, Any?> = when {
    colorKey != null -> mapOf(CalendarContract.Events.EVENT_COLOR_KEY to colorKey)
    color != null -> mapOf(
        CalendarContract.Events.EVENT_COLOR_KEY to null,
        CalendarContract.Events.EVENT_COLOR to color,
    )
    else -> mapOf(
        CalendarContract.Events.EVENT_COLOR_KEY to null,
        CalendarContract.Events.EVENT_COLOR to null,
    )
}

/**
 * UTC millis of the last second of the local day *before* the occurrence at
 * [beginMillis] in [zone] — the cutoff for truncating a series with UNTIL.
 * The provider's recurrence engine applies UNTIL coarsely (observed on a
 * Pixel: an occurrence one second *after* UNTIL was still generated), so the
 * series must end on the previous day, not one second before the occurrence.
 * With no sub-daily frequencies that is semantically the same cut.
 */
internal fun previousLocalDayEndUtcMillis(beginMillis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(beginMillis).atZone(zone).toLocalDate()
        .atStartOfDay(zone).toInstant().toEpochMilli() - 1_000L

private const val MILLIS_PER_DAY = 86_400_000L

internal fun Availability.toProviderValue(): Int = when (this) {
    Availability.Busy -> CalendarContract.Events.AVAILABILITY_BUSY
    Availability.Free -> CalendarContract.Events.AVAILABILITY_FREE
    Availability.Tentative -> CalendarContract.Events.AVAILABILITY_TENTATIVE
}

internal fun AccessLevel.toProviderValue(): Int = when (this) {
    AccessLevel.Default -> CalendarContract.Events.ACCESS_DEFAULT
    AccessLevel.Confidential -> CalendarContract.Events.ACCESS_CONFIDENTIAL
    AccessLevel.Private -> CalendarContract.Events.ACCESS_PRIVATE
    AccessLevel.Public -> CalendarContract.Events.ACCESS_PUBLIC
}

/**
 * The `Events` row for a `.ics` event being imported into [calendarId].
 *
 * A recurring row carries RRULE + DURATION (and any EXDATE) with no DTEND; a
 * one-off carries DTEND. All-day rows live at UTC midnights, exactly as the
 * parser hands them over.
 *
 * [palette] is the target account's published event colours. A calendar that
 * publishes one rejects a raw `EVENT_COLOR`, so an imported colour — which is
 * an arbitrary ARGB from a foreign app — is snapped to the nearest key it does
 * accept; accounts with no palette take the raw value (see [eventColorColumns]).
 */
internal fun buildImportedEventValues(
    event: ParsedIcsEvent,
    calendarId: Long,
    uid: String,
    palette: List<EventColorOption>,
): Map<String, Any?> = buildMap {
    val startMillis = event.start.toEpochMilliseconds()
    val endMillis = event.end.toEpochMilliseconds()
    put(CalendarContract.Events.CALENDAR_ID, calendarId)
    put(CalendarContract.Events.UID_2445, uid)
    put(CalendarContract.Events.TITLE, event.summary.trim())
    put(CalendarContract.Events.ALL_DAY, if (event.isAllDay) 1 else 0)
    put(CalendarContract.Events.DTSTART, startMillis)
    if (event.recurrenceRule == null) {
        put(CalendarContract.Events.DTEND, endMillis)
    } else {
        put(CalendarContract.Events.RRULE, event.recurrenceRule)
        put(
            CalendarContract.Events.DURATION,
            importDuration(startMillis, endMillis, event.isAllDay),
        )
        event.exDates.takeIf { it.isNotEmpty() }
            ?.let { put(CalendarContract.Events.EXDATE, it.joinToString(",")) }
    }
    // All-day rows live at UTC midnights (the file already encodes them so);
    // timed rows keep the event's own zone.
    put(CalendarContract.Events.EVENT_TIMEZONE, if (event.isAllDay) "UTC" else event.zoneId)
    put(CalendarContract.Events.AVAILABILITY, event.availability.toProviderValue())
    put(CalendarContract.Events.STATUS, event.status.toProviderStatus())
    event.location?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
    event.description?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { put(CalendarContract.Events.DESCRIPTION, it) }
    event.color?.let { color ->
        val key = palette.nearestTo(color)?.key
        putAll(eventColorColumns(colorKey = key, color = if (key == null) color else null))
    }
}

/**
 * Provider `DURATION` for an imported recurring row: whole days for an all-day
 * series, seconds otherwise. An all-day series is never shorter than a day —
 * the provider expands a zero-length one into no instances at all, so the
 * series would vanish (which is what a literal read of the Fossify family's
 * inclusive DTEND used to produce; see "Importing foreign .ics" in
 * docs/ARCHITECTURE.md).
 */
private fun importDuration(startMillis: Long, endMillis: Long, isAllDay: Boolean): String {
    val span = (endMillis - startMillis).coerceAtLeast(0)
    return if (isAllDay) "P${(span / 86_400_000L).coerceAtLeast(1)}D" else "P${span / 1_000L}S"
}

private fun EventStatus.toProviderStatus(): Int = when (this) {
    EventStatus.Confirmed -> CalendarContract.Events.STATUS_CONFIRMED
    EventStatus.Tentative -> CalendarContract.Events.STATUS_TENTATIVE
    EventStatus.Cancelled -> CalendarContract.Events.STATUS_CANCELED
}
