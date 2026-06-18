package de.jeanlucmakiola.calendula.data.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import de.jeanlucmakiola.calendula.domain.Attendee
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventColorOption
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.EventStatus
import de.jeanlucmakiola.calendula.domain.Reminder
import de.jeanlucmakiola.calendula.domain.ics.IcsEvent
import de.jeanlucmakiola.calendula.domain.ics.ParsedIcsEvent
import de.jeanlucmakiola.calendula.domain.rruleTruncatedAt
import kotlinx.datetime.toJavaLocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain-shaped seam over Android's ContentResolver. Returns parsed lists so
 * the repository can be unit-tested without constructing Cursors or
 * ContentObservers on the JVM.
 *
 * Cursor handling and the ContentObserver-to-listener bridge live entirely
 * in AndroidCalendarDataSource.
 */
interface CalendarDataSource {
    fun calendars(): List<CalendarSource>
    fun instances(beginMillis: Long, endMillis: Long): List<EventInstance>
    fun eventDetail(eventId: Long): EventDetail?

    /**
     * The event-colour palette the calendar's account publishes
     * (`CalendarContract.Colors`, `TYPE_EVENT`), sorted by key. Empty when the
     * account exposes no palette (most local calendars, some CalDAV) — the
     * signal that a custom colour can only be written as a raw `EVENT_COLOR`,
     * which a synced calendar may drop on its next sync.
     */
    fun eventColorPalette(calendarId: Long): List<EventColorOption>

    /**
     * Every master/one-off event of the writable local calendars, mapped for a
     * whole-calendar `.ics` backup. Modified-occurrence and cancelled-exception
     * rows are excluded (see [EventExportProjection]).
     */
    fun exportableEvents(): List<IcsEvent>

    /**
     * The non-empty `Events.UID_2445` values present in [calendarId] — used to
     * dedup an `.ics` import so re-importing a backup doesn't double events.
     */
    fun existingUids(calendarId: Long): Set<String>

    /**
     * Insert a parsed `.ics` event into [calendarId], preserving its UID (or
     * minting one when absent); returns the new `Events._ID`. Reminders are
     * written as the file's raw lead minutes (METHOD_ALERT).
     */
    fun insertImportedEvent(event: ParsedIcsEvent, calendarId: Long): Long

    /**
     * Create a new device-only (`ACCOUNT_TYPE_LOCAL`) calendar the app owns;
     * returns its `Calendars._ID`. Inserted through the sync-adapter URI so the
     * provider keeps the row (a plain insert is rejected for the LOCAL account).
     */
    fun createLocalCalendar(displayName: String, color: Int, description: String?): Long

    /** Update name, color and description of a local calendar the app owns. */
    fun updateCalendar(id: Long, displayName: String, color: Int, description: String?)

    /** Permanently delete a local calendar the app owns, with all its events. */
    fun deleteCalendar(id: Long)

    /**
     * Insert a new event; returns the new `Events._ID`. [allDayReminderTimeMinutes]
     * (minutes from local midnight) is the wall-clock time all-day reminders
     * should fire at — encoded into each all-day reminder's provider offset
     * (ignored for timed events).
     */
    fun insertEvent(form: EventForm, allDayReminderTimeMinutes: Int): Long

    /**
     * Update an existing event (for recurring events: the whole series) to
     * match [updated]. [original] is the form as it was prefilled from the
     * event, so only fields the user actually changed are written and the
     * reminder rows can be diffed instead of wiped.
     * [allDayReminderTimeMinutes]: see [insertEvent].
     */
    fun updateEvent(
        eventId: Long,
        original: EventForm,
        updated: EventForm,
        allDayReminderTimeMinutes: Int,
    )

    /**
     * Change a single occurrence of a recurring event by inserting a
     * modified-occurrence exception at [beginMillis] (the occurrence's
     * `Instances.BEGIN`) carrying [form]'s values; returns the exception
     * row's `Events._ID`. [allDayReminderTimeMinutes]: see [insertEvent].
     */
    fun updateOccurrence(
        eventId: Long,
        beginMillis: Long,
        form: EventForm,
        allDayReminderTimeMinutes: Int,
    ): Long

    /**
     * Change a recurring event from the occurrence at [beginMillis] onwards
     * by splitting the series: the existing RRULE ends just before the
     * occurrence and a new event with [updated]'s values (and rule) starts
     * there; returns the new event's `Events._ID`. From the first occurrence
     * this is a plain series update. A carried-over COUNT restarts counting
     * in the new series (we don't recompute the remaining occurrences).
     */
    fun updateEventFromOccurrence(
        eventId: Long,
        beginMillis: Long,
        original: EventForm,
        updated: EventForm,
        allDayReminderTimeMinutes: Int,
    ): Long

    /**
     * Delete a recurring event from the occurrence at [beginMillis] onwards
     * by ending the series RRULE just before it. Deleting from the first
     * occurrence removes the whole event.
     */
    fun deleteEventFromOccurrence(eventId: Long, beginMillis: Long)

    /** Delete the whole event (for recurring events: the entire series). */
    fun deleteEvent(eventId: Long)

    /**
     * Cancel a single occurrence of a recurring event by inserting a
     * cancelled exception at [beginMillis] (the occurrence's `Instances.BEGIN`).
     */
    fun deleteOccurrence(eventId: Long, beginMillis: Long)

    fun registerChangeListener(listener: () -> Unit)
    fun unregisterChangeListener(listener: () -> Unit)
}

@Singleton
class AndroidCalendarDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : CalendarDataSource {

    private val resolver: ContentResolver get() = context.contentResolver
    private val observers = mutableMapOf<() -> Unit, ContentObserver>()

    override fun calendars(): List<CalendarSource> = resolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        CalendarProjection.COLUMNS,
        null, null,
        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC",
    )?.use { it.mapAll(::toCalendarSource) } ?: emptyList()

    /**
     * Calendar-row writes must address the provider as a sync adapter and name
     * the account in the URI; otherwise inserts/deletes for the LOCAL account
     * are silently dropped or only soft-deleted.
     */
    private fun localCalendarsUri(): Uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT_NAME)
        .appendQueryParameter(
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.ACCOUNT_TYPE_LOCAL,
        )
        .build()

    override fun createLocalCalendar(displayName: String, color: Int, description: String?): Long {
        val name = displayName.trim().ifEmpty { Fallbacks.UNNAMED_CALENDAR }
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, LOCAL_ACCOUNT_NAME)
            // NAME is the sync-adapter id; DISPLAY_NAME is what the user sees.
            put(CalendarContract.Calendars.NAME, name)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, name)
            put(CalendarContract.Calendars.CALENDAR_COLOR, color)
            put(
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CAL_ACCESS_OWNER,
            )
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            putDescription(description)
        }
        val uri = resolver.insert(localCalendarsUri(), values)
            // No calendar name in the message — it can reach a crash report.
            ?: throw WriteFailedException("create local calendar")
        return ContentUris.parseId(uri)
    }

    override fun updateCalendar(id: Long, displayName: String, color: Int, description: String?) {
        val name = displayName.trim().ifEmpty { Fallbacks.UNNAMED_CALENDAR }
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, name)
            put(CalendarContract.Calendars.NAME, name)
            put(CalendarContract.Calendars.CALENDAR_COLOR, color)
            putDescription(description)
        }
        val rows = resolver.update(
            ContentUris.withAppendedId(localCalendarsUri(), id),
            values, null, null,
        )
        if (rows == 0) throw WriteFailedException("update calendar id=$id")
    }

    /** Store the description in CAL_SYNC1, or clear it when blank/absent. */
    private fun ContentValues.putDescription(description: String?) {
        val text = description?.trim().orEmpty()
        if (text.isEmpty()) {
            putNull(CalendarProjection.DESCRIPTION_COLUMN)
        } else {
            put(CalendarProjection.DESCRIPTION_COLUMN, text)
        }
    }

    override fun deleteCalendar(id: Long) {
        val deleted = resolver.delete(
            ContentUris.withAppendedId(localCalendarsUri(), id),
            null, null,
        )
        if (deleted == 0) throw WriteFailedException("delete calendar id=$id")
    }

    override fun instances(beginMillis: Long, endMillis: Long): List<EventInstance> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, beginMillis)
            ContentUris.appendId(this, endMillis)
        }.build()
        return resolver.query(
            uri,
            InstanceProjection.COLUMNS,
            null, null,
            CalendarContract.Instances.BEGIN + " ASC",
        )?.use { c -> c.mapAllNotNull { CursorColumnReader(c).toEventInstance() } } ?: emptyList()
    }

    override fun eventDetail(eventId: Long): EventDetail? {
        val attendees = queryAttendees(eventId)
        val reminders = queryReminders(eventId)
        return resolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            EventDetailProjection.COLUMNS,
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) null
            else CursorColumnReader(c).toEventDetailCore(attendees, reminders)
        }
    }

    override fun eventColorPalette(calendarId: Long): List<EventColorOption> {
        val account = calendarAccount(calendarId) ?: return emptyList()
        return resolver.query(
            CalendarContract.Colors.CONTENT_URI,
            arrayOf(CalendarContract.Colors.COLOR_KEY, CalendarContract.Colors.COLOR),
            CalendarContract.Colors.ACCOUNT_NAME + " = ? AND " +
                CalendarContract.Colors.ACCOUNT_TYPE + " = ? AND " +
                CalendarContract.Colors.COLOR_TYPE + " = ?",
            arrayOf(
                account.name,
                account.type,
                CalendarContract.Colors.TYPE_EVENT.toString(),
            ),
            null,
        )?.use { c ->
            c.mapAll { EventColorOption(key = it.getString(0).orEmpty(), argb = it.getInt(1)) }
        }
            ?.filter { it.key.isNotEmpty() }
            ?.sortedBy { it.key }
            ?: emptyList()
    }

    override fun exportableEvents(): List<IcsEvent> {
        // Only the local calendars the app owns and can write — synced calendars
        // already have a backup (their server). Map id → display name for the
        // X-CALENDULA-CALENDAR tag a restore uses to fan back out.
        val names = calendars()
            .filter { it.isLocal && it.canModifyContents }
            .associate { it.id to it.displayName }
        if (names.isEmpty()) return emptyList()

        val idList = names.keys.joinToString(",")
        return resolver.query(
            CalendarContract.Events.CONTENT_URI,
            EventExportProjection.COLUMNS,
            // Skip soft-deleted rows and exception rows (modified occurrences /
            // cancellations) — v1 exports masters + one-offs only.
            "${CalendarContract.Events.CALENDAR_ID} IN ($idList) AND " +
                "${CalendarContract.Events.DELETED} = 0 AND " +
                "${CalendarContract.Events.ORIGINAL_ID} IS NULL",
            null,
            CalendarContract.Events.DTSTART + " ASC",
        )?.use { c ->
            c.mapAll {
                val reader = CursorColumnReader(c)
                val eventId = reader.getLong(EventExportProjection.IDX_ID)
                val calendarId = reader.getLong(EventExportProjection.IDX_CALENDAR_ID)
                reader.toIcsEvent(
                    reminderMinutes = queryReminders(eventId).map { it.minutes },
                    calendarName = names[calendarId],
                )
            }
        } ?: emptyList()
    }

    override fun existingUids(calendarId: Long): Set<String> = resolver.query(
        CalendarContract.Events.CONTENT_URI,
        arrayOf(CalendarContract.Events.UID_2445),
        "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
            "${CalendarContract.Events.UID_2445} IS NOT NULL",
        arrayOf(calendarId.toString()),
        null,
    )?.use { c ->
        buildSet { while (c.moveToNext()) c.getString(0)?.takeIf { it.isNotEmpty() }?.let(::add) }
    } ?: emptySet()

    override fun insertImportedEvent(event: ParsedIcsEvent, calendarId: Long): Long {
        val startMillis = event.start.toEpochMillis()
        val endMillis = event.end.toEpochMillis()
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            // Preserve the file's UID so a re-import dedups against it; mint one
            // only when the source event carried none.
            put(
                CalendarContract.Events.UID_2445,
                event.uid?.takeIf { it.isNotBlank() } ?: "${UUID.randomUUID()}@calendula",
            )
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
        }
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: throw WriteFailedException("import event into calendar id=$calendarId")
        val eventId = ContentUris.parseId(uri)
        // Raw lead minutes straight from the file's VALARMs (best-effort, like insertEvent).
        event.reminderMinutes.distinct().filter { it >= 0 }.forEach { minutes ->
            val reminder = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, minutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            if (resolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder) == null) {
                Log.w(TAG, "Failed to attach reminder ($minutes min) to imported event $eventId")
            }
        }
        return eventId
    }

    /** Provider DURATION for an imported recurring row: whole days / seconds. */
    private fun importDuration(startMillis: Long, endMillis: Long, isAllDay: Boolean): String {
        val span = (endMillis - startMillis).coerceAtLeast(0)
        return if (isAllDay) "P${span / 86_400_000L}D" else "P${span / 1_000L}S"
    }

    private fun EventStatus.toProviderStatus(): Int = when (this) {
        EventStatus.Confirmed -> CalendarContract.Events.STATUS_CONFIRMED
        EventStatus.Tentative -> CalendarContract.Events.STATUS_TENTATIVE
        EventStatus.Cancelled -> CalendarContract.Events.STATUS_CANCELED
    }

    /** The account a calendar belongs to, for scoping a `Colors` lookup. */
    private fun calendarAccount(calendarId: Long): CalendarAccount? = resolver.query(
        ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId),
        arrayOf(
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
        ),
        null, null, null,
    )?.use { c ->
        if (c.moveToFirst()) {
            CalendarAccount(name = c.getString(0).orEmpty(), type = c.getString(1).orEmpty())
        } else {
            null
        }
    }

    private data class CalendarAccount(val name: String, val type: String)

    /**
     * The raw provider `MINUTES` to store for one of [form]'s reminders: an
     * all-day reminder is shifted to fire at [allDayReminderTimeMinutes] local
     * (see [toProviderAllDayMinutes]); a timed reminder is its lead time as-is.
     */
    private fun providerReminderMinutes(
        form: EventForm,
        minutes: Int,
        allDayReminderTimeMinutes: Int,
    ): Int = if (form.isAllDay) {
        toProviderAllDayMinutes(
            semanticMinutes = minutes,
            startDate = form.start.date.toJavaLocalDate(),
            zone = ZoneId.systemDefault(),
            timeOfDayMinutes = allDayReminderTimeMinutes,
        )
    } else {
        minutes
    }

    /** [form]'s reminders as the distinct raw provider offsets to store. */
    private fun encodedReminders(form: EventForm, allDayReminderTimeMinutes: Int): List<Int> =
        form.reminders
            .map { providerReminderMinutes(form, it, allDayReminderTimeMinutes) }
            .distinct()

    override fun insertEvent(form: EventForm, allDayReminderTimeMinutes: Int): Long {
        val times = form.toWriteTimes(ZoneId.systemDefault())
        val values = ContentValues().apply {
            put(
                CalendarContract.Events.CALENDAR_ID,
                requireNotNull(form.calendarId) { "EventForm.calendarId is required" },
            )
            // A globally-unique UID so a later .ics backup/restore can identify
            // the event and not duplicate it on re-import (the provider leaves
            // this null for events it didn't sync). Older rows without one fall
            // back to a stable synthesised UID at export time (deriveIcsUid).
            put(CalendarContract.Events.UID_2445, "${UUID.randomUUID()}@calendula")
            put(CalendarContract.Events.TITLE, form.title.trim())
            put(CalendarContract.Events.ALL_DAY, if (form.isAllDay) 1 else 0)
            put(CalendarContract.Events.DTSTART, times.dtStartMillis)
            // The provider's invariant: recurring rows carry RRULE+DURATION
            // (and no DTEND), one-off rows carry DTEND.
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
            // A null colour just leaves both columns unset (the event inherits
            // its calendar's colour), so only the key/raw cases are written.
            when {
                form.colorKey != null ->
                    put(CalendarContract.Events.EVENT_COLOR_KEY, form.colorKey)
                form.color != null -> put(CalendarContract.Events.EVENT_COLOR, form.color)
            }
        }
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: throw WriteFailedException("insert event into calendar id=${form.calendarId}")
        val eventId = ContentUris.parseId(uri)
        // Best effort (spec §8): the event exists at this point — a reminder
        // that fails to attach is logged, not surfaced as a failed create.
        encodedReminders(form, allDayReminderTimeMinutes)
            .forEach { minutes ->
                val reminder = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, minutes)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                if (resolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder) == null) {
                    Log.w(TAG, "Failed to attach reminder ($minutes min) to event $eventId")
                }
            }
        return eventId
    }

    override fun updateEvent(
        eventId: Long,
        original: EventForm,
        updated: EventForm,
        allDayReminderTimeMinutes: Int,
    ) {
        val values = buildEventUpdateValues(
            original = original,
            updated = updated,
            seriesDtStartMillis = querySeriesRow(eventId).dtStartMillis,
            zone = ZoneId.systemDefault(),
        )
        if (values.isNotEmpty()) {
            val rows = resolver.update(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                values.toContentValues(),
                null, null,
            )
            if (rows == 0) throw WriteFailedException("update event id=$eventId")
        }
        // Untouched reminder sets are left alone so unrelated edits can't
        // disturb provider rows the form never knew about. The diff is on the
        // form's semantic minutes; reconcile works in encoded provider minutes.
        if (updated.reminders.toSet() != original.reminders.toSet()) {
            reconcileReminders(eventId, encodedReminders(updated, allDayReminderTimeMinutes))
        }
    }

    override fun updateOccurrence(
        eventId: Long,
        beginMillis: Long,
        form: EventForm,
        allDayReminderTimeMinutes: Int,
    ): Long {
        // The provider clones the series row and applies these values on top.
        val values = buildOccurrenceExceptionValues(
            form = form,
            originalInstanceMillis = beginMillis,
            zone = ZoneId.systemDefault(),
        )
        val uri = resolver.insert(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, eventId),
            values.toContentValues(),
        ) ?: throw WriteFailedException("modify occurrence event id=$eventId begin=$beginMillis")
        val exceptionId = ContentUris.parseId(uri)
        // Whether the provider copied the parent's reminder rows is its
        // business — reconciling against the actual rows handles both ways.
        reconcileReminders(exceptionId, encodedReminders(form, allDayReminderTimeMinutes))
        return exceptionId
    }

    override fun updateEventFromOccurrence(
        eventId: Long,
        beginMillis: Long,
        original: EventForm,
        updated: EventForm,
        allDayReminderTimeMinutes: Int,
    ): Long {
        val row = querySeriesRow(eventId)
        // From the first occurrence on (or with no rule to split) this is
        // just a series update.
        if (row.rrule.isNullOrBlank() || beginMillis <= row.dtStartMillis) {
            updateEvent(eventId, original, updated, allDayReminderTimeMinutes)
            return eventId
        }
        // Insert the new series first: if it fails, the original is untouched.
        val newEventId = insertEvent(updated, allDayReminderTimeMinutes)
        truncateSeries(eventId, row, beginMillis)
        return newEventId
    }

    override fun deleteEventFromOccurrence(eventId: Long, beginMillis: Long) {
        val row = querySeriesRow(eventId)
        // From the first occurrence on = the whole series; also the fallback
        // when there is no RRULE to truncate.
        if (row.rrule.isNullOrBlank() || beginMillis <= row.dtStartMillis) {
            deleteEvent(eventId)
            return
        }
        truncateSeries(eventId, row, beginMillis)
    }

    /**
     * End [row]'s series just before the occurrence at [beginMillis]. The
     * provider regenerates an event's cached instances only from the values
     * carried by the update itself — an RRULE-only update leaves the old
     * instances standing (observed on-device: the truncated occurrence kept
     * showing) — so the entire time-related set travels together, with only
     * the RRULE actually changing.
     */
    private fun truncateSeries(eventId: Long, row: SeriesRow, beginMillis: Long) {
        requireNotNull(row.rrule) { "truncateSeries needs a recurring row" }
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, row.dtStartMillis)
            put(CalendarContract.Events.DURATION, row.duration)
            put(CalendarContract.Events.EVENT_TIMEZONE, row.timezone)
            put(CalendarContract.Events.ALL_DAY, row.allDay)
            put(
                CalendarContract.Events.RRULE,
                rruleTruncatedAt(row.rrule, untilUtcMillis = row.truncationCutoff(beginMillis)),
            )
        }
        val rows = resolver.update(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            values,
            null, null,
        )
        if (rows == 0) {
            throw WriteFailedException("truncate series event id=$eventId begin=$beginMillis")
        }
    }

    /** The series anchor: every time-related column of the Events row. */
    private fun querySeriesRow(eventId: Long): SeriesRow = resolver.query(
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
        arrayOf(
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
        ),
        null, null, null,
    )?.use { c ->
        if (c.moveToFirst()) {
            SeriesRow(
                dtStartMillis = c.getLong(0),
                rrule = c.getString(1),
                timezone = c.getString(2),
                duration = c.getString(3),
                allDay = c.getInt(4),
            )
        } else {
            null
        }
    } ?: throw WriteFailedException("read series row of event id=$eventId")

    private data class SeriesRow(
        val dtStartMillis: Long,
        val rrule: String?,
        val timezone: String?,
        val duration: String?,
        val allDay: Int,
    ) {
        /** UNTIL cutoff for ending the series before the occurrence at [beginMillis]. */
        fun truncationCutoff(beginMillis: Long): Long = previousLocalDayEndUtcMillis(
            beginMillis = beginMillis,
            zone = runCatching { ZoneId.of(timezone ?: "UTC") }.getOrDefault(ZoneOffset.UTC),
        )
    }

    /**
     * Make the event's reminder rows match [targetMinutes] — the raw provider
     * offsets to store (already encoded via [encodedReminders], so all-day shifts
     * are baked in and the diff matches the stored rows). Rows with other offsets
     * are deleted, missing ones inserted as best-effort ALERTs (like insertEvent).
     * Rows whose minutes survive keep their method.
     */
    private fun reconcileReminders(eventId: Long, targetMinutes: List<Int>) {
        val target = targetMinutes.toSet()
        val existing = queryReminders(eventId).map { it.minutes }.toSet()
        (existing - target).forEach { minutes ->
            resolver.delete(
                CalendarContract.Reminders.CONTENT_URI,
                CalendarContract.Reminders.EVENT_ID + " = ? AND " +
                    CalendarContract.Reminders.MINUTES + " = ?",
                arrayOf(eventId.toString(), minutes.toString()),
            )
        }
        (target - existing).forEach { minutes ->
            val reminder = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, minutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            if (resolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder) == null) {
                Log.w(TAG, "Failed to attach reminder ($minutes min) to event $eventId")
            }
        }
    }

    private fun Map<String, Any?>.toContentValues(): ContentValues =
        ContentValues().also { cv ->
            forEach { (column, value) ->
                when (value) {
                    null -> cv.putNull(column)
                    is String -> cv.put(column, value)
                    is Long -> cv.put(column, value)
                    is Int -> cv.put(column, value)
                    // Only the type, never the value — a cell value can be event content.
                    else -> error("Unsupported value type for column '$column': ${value::class.simpleName}")
                }
            }
        }

    override fun deleteEvent(eventId: Long) {
        val deleted = resolver.delete(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            null, null,
        )
        if (deleted == 0) throw WriteFailedException("delete event id=$eventId")
    }

    override fun deleteOccurrence(eventId: Long, beginMillis: Long) {
        // A cancelled exception row hides exactly this occurrence; the sync
        // adapter turns it into an EXDATE/cancelled VEVENT upstream.
        val values = ContentValues().apply {
            put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, beginMillis)
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
        }
        val uri = ContentUris.withAppendedId(
            CalendarContract.Events.CONTENT_EXCEPTION_URI, eventId,
        )
        resolver.insert(uri, values)
            ?: throw WriteFailedException("cancel occurrence event id=$eventId begin=$beginMillis")
    }

    override fun registerChangeListener(listener: () -> Unit) {
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                listener()
            }
        }
        observers[listener] = obs
        resolver.registerContentObserver(
            CalendarContract.CONTENT_URI,
            /* notifyForDescendants = */ true,
            obs,
        )
    }

    override fun unregisterChangeListener(listener: () -> Unit) {
        observers.remove(listener)?.let { resolver.unregisterContentObserver(it) }
    }

    private fun queryAttendees(eventId: Long): List<Attendee> = resolver.query(
        CalendarContract.Attendees.CONTENT_URI,
        AttendeeProjection.COLUMNS,
        CalendarContract.Attendees.EVENT_ID + " = ?",
        arrayOf(eventId.toString()),
        null,
    )?.use { c -> c.mapAll { CursorColumnReader(c).toAttendee() } } ?: emptyList()

    private fun queryReminders(eventId: Long): List<Reminder> = resolver.query(
        CalendarContract.Reminders.CONTENT_URI,
        ReminderProjection.COLUMNS,
        CalendarContract.Reminders.EVENT_ID + " = ?",
        arrayOf(eventId.toString()),
        null,
    )?.use { c -> c.mapAll { CursorColumnReader(c).toReminder() } } ?: emptyList()

    private fun toCalendarSource(c: Cursor): CalendarSource = CursorColumnReader(c).toCalendarSource()

    /** Iterate every row and map; skips nothing. */
    private inline fun <T> Cursor.mapAll(mapper: (Cursor) -> T): List<T> = buildList {
        while (moveToNext()) add(mapper(this@mapAll))
    }

    /** Iterate every row and map; drops nulls. */
    private inline fun <T : Any> Cursor.mapAllNotNull(mapper: (Cursor) -> T?): List<T> = buildList {
        while (moveToNext()) mapper(this@mapAllNotNull)?.let(::add)
    }

    private companion object {
        const val TAG = "CalendarDataSource"

        /**
         * Shared account for every app-created local calendar, so they group
         * together (by account) in the filter sheet and calendar manager.
         */
        const val LOCAL_ACCOUNT_NAME = "Calendula"
    }
}
