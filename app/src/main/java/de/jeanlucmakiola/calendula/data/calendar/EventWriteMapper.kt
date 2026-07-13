package de.jeanlucmakiola.calendula.data.calendar

import android.provider.CalendarContract
import de.jeanlucmakiola.calendula.domain.AccessLevel
import de.jeanlucmakiola.calendula.domain.Availability
import de.jeanlucmakiola.calendula.domain.EventForm
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Provider-ready DTSTART / DTEND / EVENT_TIMEZONE for an event write. */
internal data class EventWriteTimes(
    val dtStartMillis: Long,
    val dtEndMillis: Long,
    val timezone: String,
)

/**
 * All-day events live at UTC midnights with an exclusive DTEND (the
 * CalendarContract convention — a one-day event ends at the next midnight);
 * timed events resolve their wall-clock values in [zone].
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
    EventWriteTimes(
        dtStartMillis = start.toJavaLocalDateTime().atZone(zone).toInstant().toEpochMilli(),
        dtEndMillis = end.toJavaLocalDateTime().atZone(zone).toInstant().toEpochMilli(),
        timezone = zone.id,
    )
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
 * - recurring result → the *series* DTSTART moves by the same delta the user
 *   applied to the displayed occurrence ([seriesDtStartMillis] is the row's
 *   current DTSTART), DURATION replaces DTEND, RRULE is written. This keeps
 *   past occurrences intact when someone edits a later occurrence's time.
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

    val timesChanged = updated.start != original.start ||
        updated.end != original.end ||
        updated.isAllDay != original.isAllDay ||
        updated.rrule != original.rrule
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
        val startDelta = newTimes.dtStartMillis - original.toWriteTimes(zone).dtStartMillis
        put(CalendarContract.Events.DTSTART, seriesDtStartMillis + startDelta)
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
