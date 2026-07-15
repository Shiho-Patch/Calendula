package de.jeanlucmakiola.calendula.data.calendar

import de.jeanlucmakiola.floret.time.toEpochMillis
import de.jeanlucmakiola.floret.time.toKotlinInstantFromEpochMillis
import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.jeanlucmakiola.calendula.domain.Attendee
import de.jeanlucmakiola.calendula.domain.AttendeeRelationship
import de.jeanlucmakiola.calendula.domain.AttendeeType
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.EventAttendee
import de.jeanlucmakiola.calendula.domain.EventColorOption
import de.jeanlucmakiola.calendula.domain.EventDetail
import de.jeanlucmakiola.calendula.domain.curatedForPicker
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.EventInstance
import de.jeanlucmakiola.calendula.domain.EventStatus
import de.jeanlucmakiola.calendula.domain.Reminder
import de.jeanlucmakiola.calendula.domain.contacts.MANAGED_UID_PREFIX
import de.jeanlucmakiola.calendula.domain.contacts.SpecialDateType
import de.jeanlucmakiola.calendula.domain.ics.IcsEvent
import de.jeanlucmakiola.calendula.domain.ics.ParsedIcsEvent
import de.jeanlucmakiola.calendula.domain.rruleTruncatedAt
import kotlinx.datetime.toJavaLocalDate
import java.time.Instant
import java.time.LocalDate
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
     * Master/one-off events whose title, description or location contains
     * [query] (case-insensitive), across all calendars, newest first. Reads the
     * Events table directly so the search is unbounded in time; exception rows
     * are excluded (see [SearchProjection]). [query] is assumed non-blank.
     */
    fun searchEvents(query: String): List<EventInstance>

    /**
     * The event-colour palette the calendar's account publishes
     * (`CalendarContract.Colors`, `TYPE_EVENT`), curated for display — deduped,
     * thinned to visually distinct swatches when oversized (CalDAV adapters
     * publish all ~147 CSS3 names, #22) and hue-sorted; see [curatedForPicker].
     * Empty when the account exposes no palette (most local calendars, some
     * CalDAV) — the signal that a custom colour can only be written as a raw
     * `EVENT_COLOR`, which a synced calendar may drop on its next sync.
     */
    fun eventColorPalette(calendarId: Long): List<EventColorOption>

    /**
     * Every master/one-off event of the writable local calendars, mapped for a
     * whole-calendar `.ics` backup. Modified-occurrence and cancelled-exception
     * rows are excluded (see [EventExportProjection]). When [calendarIds] is
     * given, only those calendars are exported (still intersected with the
     * eligible set); `null` exports every eligible calendar.
     */
    fun exportableEvents(calendarIds: Set<Long>? = null): List<IcsEvent>

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
     * Create a local calendar tagged as the special-dates mirror for [type]
     * (a marker in `CAL_SYNC2`); returns its `Calendars._ID`. Otherwise a normal
     * local calendar, so it inherits per-calendar colour/visibility/reminders.
     */
    fun createManagedCalendar(displayName: String, color: Int, type: SpecialDateType): Long

    /**
     * The app's special-dates calendars, found by their `CAL_SYNC2` marker.
     * Lets the mirror re-adopt its calendars after the stored ids are lost, and
     * detect when the user deleted one in system settings.
     */
    fun findManagedCalendars(): List<ManagedCalendarRow>

    /**
     * The managed events in [calendarId] (those carrying a `UID_2445`, not
     * soft-deleted) as [ManagedEventRow]s — the existing side of the sync diff.
     */
    fun queryManagedEvents(calendarId: Long): List<ManagedEventRow>

    /**
     * Insert a managed event carrying the deterministic [uid] in `UID_2445`;
     * seeds its reminder rows from [form] (never touched again by sync). Returns
     * the new `Events._ID`. [allDayReminderTimeMinutes]: see [insertEvent].
     */
    fun insertManagedEvent(form: EventForm, uid: String, allDayReminderTimeMinutes: Int): Long

    /**
     * Overwrite exactly the given managed columns on [eventId] (keys are
     * `CalendarContract.Events` columns; a null value clears the column). A
     * bare, targeted update — it never reconciles reminders or attendees, so
     * user-owned data on the event survives every sync. No-ops on an empty map.
     */
    fun updateManagedFields(eventId: Long, values: Map<String, Any?>)

    /**
     * Replace the reminders on **every managed** event in [calendarId] with
     * [minutes] (all-day lead times), each encoded so it fires at
     * [allDayReminderTimeMinutes] on its upcoming occurrence. Used by the
     * special-dates section so a reminder change applies to existing events too,
     * not just future ones — the managed calendars treat reminders as a
     * calendar-level setting. Skips any non-managed (user) event in the calendar.
     */
    fun applyManagedCalendarReminders(
        calendarId: Long,
        allDayReminderTimeMinutes: Int,
        minutes: List<Int>,
    )

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
     * Move an event (for recurring events: the whole series, with its modified
     * and cancelled occurrences) to [targetCalendarId], returning the new
     * `Events._ID`. `CALENDAR_ID` is sync-adapter-owned and can't be updated in
     * place, so this is copy+delete: the row is re-inserted on the target (its
     * `UID_2445` preserved), its exceptions, reminders and editable guests
     * replayed, the [original]→[updated] field edits applied, then the source
     * deleted. The source is removed only once the copy fully succeeds; a failure
     * before that rolls the new event back, leaving the original untouched.
     * [allDayReminderTimeMinutes]: see [insertEvent].
     */
    fun moveEvent(
        eventId: Long,
        targetCalendarId: Long,
        original: EventForm,
        updated: EventForm,
        allDayReminderTimeMinutes: Int,
    ): Long

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

    // All access to these two collections is guarded by [observerLock] because
    // listeners are registered on the main thread (repository init, via ViewModel
    // creation) while [ensureObserversRegistered] runs on the IO dispatcher.
    private val observerLock = Any()
    private val observers = mutableMapOf<() -> Unit, ContentObserver>()
    private val registeredObservers = mutableSetOf<ContentObserver>()

    private fun hasCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Attach any not-yet-registered observers to the provider, but only once the
     * calendar permission is held. Registering a ContentObserver on the calendar
     * provider without that permission throws SecurityException on newer Android
     * (it used to silently no-op), which crashed the app at launch — the repo
     * registers its observer eagerly, before the permission gate. Called from the
     * observed reads ([calendars]/[instances]) so the observer re-attaches the
     * first time a screen queries after the permission is granted.
     */
    private fun ensureObserversRegistered() {
        if (!hasCalendarPermission()) return
        synchronized(observerLock) {
            if (registeredObservers.size == observers.size) return
            observers.values.forEach(::registerObserverLocked)
        }
    }

    private fun registerObserverLocked(obs: ContentObserver) {
        if (obs in registeredObservers || !hasCalendarPermission()) return
        runCatching {
            resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, obs)
        }.onSuccess { registeredObservers += obs }
            .onFailure { Log.w(TAG, "Calendar observer registration skipped", it) }
    }

    override fun calendars(): List<CalendarSource> {
        ensureObserversRegistered()
        return resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            CalendarProjection.COLUMNS,
            null, null,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " ASC",
        )?.use { it.mapAll(::toCalendarSource) } ?: emptyList()
    }

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

    override fun createManagedCalendar(displayName: String, color: Int, type: SpecialDateType): Long {
        val name = displayName.trim().ifEmpty { Fallbacks.UNNAMED_CALENDAR }
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, LOCAL_ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, LOCAL_ACCOUNT_NAME)
            put(CalendarContract.Calendars.NAME, name)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, name)
            put(CalendarContract.Calendars.CALENDAR_COLOR, color)
            put(
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CAL_ACCESS_OWNER,
            )
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            // The marker that identifies this as our special-dates calendar,
            // independent of the (user-editable) display name. CAL_SYNC1 already
            // holds the description, so the marker lives in CAL_SYNC2.
            put(MANAGED_MARKER_COLUMN, markerFor(type))
        }
        val uri = resolver.insert(localCalendarsUri(), values)
            ?: throw WriteFailedException("create managed calendar")
        return ContentUris.parseId(uri)
    }

    override fun findManagedCalendars(): List<ManagedCalendarRow> = resolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        arrayOf(CalendarContract.Calendars._ID, MANAGED_MARKER_COLUMN),
        "${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND " +
            "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND " +
            "$MANAGED_MARKER_COLUMN IS NOT NULL",
        arrayOf(CalendarContract.ACCOUNT_TYPE_LOCAL, LOCAL_ACCOUNT_NAME),
        null,
    )?.use { c ->
        buildList {
            while (c.moveToNext()) {
                val type = typeForMarker(c.getString(1)) ?: continue
                add(ManagedCalendarRow(id = c.getLong(0), type = type))
            }
        }
    } ?: emptyList()

    override fun queryManagedEvents(calendarId: Long): List<ManagedEventRow> = resolver.query(
        CalendarContract.Events.CONTENT_URI,
        arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.UID_2445,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.RRULE,
        ),
        // Only our own mirror events (UID prefix), so a user event that somehow
        // landed in this calendar (e.g. an .ics import) is never seen as
        // "existing but not desired" and deleted by the diff.
        "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
            "${CalendarContract.Events.DELETED} = 0 AND " +
            "${CalendarContract.Events.UID_2445} LIKE ?",
        arrayOf(calendarId.toString(), "$MANAGED_UID_PREFIX%"),
        null,
    )?.use { c ->
        buildList {
            while (c.moveToNext()) {
                val uid = c.getString(1)?.takeIf { it.isNotEmpty() } ?: continue
                add(
                    ManagedEventRow(
                        eventId = c.getLong(0),
                        uid = uid,
                        title = c.getString(2).orEmpty(),
                        dtStartMillis = c.getLong(3),
                        rrule = c.getString(4)?.takeIf { it.isNotEmpty() },
                    ),
                )
            }
        }
    } ?: emptyList()

    override fun insertManagedEvent(form: EventForm, uid: String, allDayReminderTimeMinutes: Int): Long {
        // Deterministic UID = the reconciliation key. A re-sync reads it back
        // (queryManagedEvents) and diffs on it, so a contact never doubles.
        val times = form.toWriteTimes(ZoneId.systemDefault())
        val values = buildEventInsertValues(form, uid, times)
        val eventId = resolver.insert(CalendarContract.Events.CONTENT_URI, values.toContentValues())
            ?.let(ContentUris::parseId)
            ?: throw WriteFailedException("insert managed event into calendar id=${form.calendarId}")
        // Seeded once, then never re-touched by sync — so a user who moves a
        // birthday reminder keeps their change. Encoded against the upcoming
        // occurrence, not the ancient DTSTART anchor (wrong timezone offset).
        val anchor = Instant.ofEpochMilli(times.dtStartMillis).atZone(ZoneOffset.UTC).toLocalDate()
        insertReminderRows(
            eventId,
            managedAllDayReminderMinutes(anchor, form.reminders, allDayReminderTimeMinutes),
        )
        return eventId
    }

    override fun updateManagedFields(eventId: Long, values: Map<String, Any?>) {
        if (values.isEmpty()) return
        val rows = resolver.update(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            values.toContentValues(),
            null, null,
        )
        if (rows == 0) throw WriteFailedException("update managed event id=$eventId")
    }

    override fun applyManagedCalendarReminders(
        calendarId: Long,
        allDayReminderTimeMinutes: Int,
        minutes: List<Int>,
    ) {
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID, CalendarContract.Events.DTSTART),
            // Only our own mirror events, so a stray user event in this calendar
            // isn't all-day-re-encoded or stripped of its own reminders.
            "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
                "${CalendarContract.Events.DELETED} = 0 AND " +
                "${CalendarContract.Events.UID_2445} LIKE ?",
            arrayOf(calendarId.toString(), "$MANAGED_UID_PREFIX%"),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val eventId = c.getLong(0)
                // Managed events are all-day: DTSTART is a UTC midnight anchor.
                val anchor = Instant.ofEpochMilli(c.getLong(1))
                    .atZone(ZoneOffset.UTC).toLocalDate()
                reconcileReminders(
                    eventId,
                    managedAllDayReminderMinutes(anchor, minutes, allDayReminderTimeMinutes),
                )
            }
        }
    }

    override fun instances(beginMillis: Long, endMillis: Long): List<EventInstance> {
        ensureObserversRegistered()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, beginMillis)
            ContentUris.appendId(this, endMillis)
        }.build()
        return resolver.query(
            uri,
            InstanceProjection.COLUMNS,
            // Hide cancelled occurrences: "delete only this event" writes a
            // cancelled exception for the one instance (#47). A NULL status is a
            // normal, un-cancelled event, so it must survive the filter — a bare
            // `!= CANCELED` would drop it (NULL != 2 is NULL, not true).
            "${CalendarContract.Instances.STATUS} IS NULL OR " +
                "${CalendarContract.Instances.STATUS} != ${CalendarContract.Events.STATUS_CANCELED}",
            null,
            CalendarContract.Instances.BEGIN + " ASC",
        )?.use { c -> c.mapAllNotNull { CursorColumnReader(c).toEventInstance() } } ?: emptyList()
    }

    override fun searchEvents(query: String): List<EventInstance> {
        ensureObserversRegistered()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        // Escape the SQL LIKE wildcards so a literal % or _ in the query matches
        // itself instead of acting as a wildcard.
        val escaped = trimmed
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        val like = "%$escaped%"
        val match = "${CalendarContract.Events.TITLE} LIKE ? ESCAPE '\\' OR " +
            "${CalendarContract.Events.DESCRIPTION} LIKE ? ESCAPE '\\' OR " +
            "${CalendarContract.Events.EVENT_LOCATION} LIKE ? ESCAPE '\\'"
        val selection = "($match) AND " +
            "${CalendarContract.Events.DELETED} = 0 AND " +
            "${CalendarContract.Events.ORIGINAL_ID} IS NULL"
        return resolver.query(
            CalendarContract.Events.CONTENT_URI,
            SearchProjection.COLUMNS,
            selection,
            arrayOf(like, like, like),
            CalendarContract.Events.DTSTART + " DESC",
        )?.use { c ->
            val reader = CursorColumnReader(c)
            val out = ArrayList<EventInstance>(c.count)
            while (c.moveToNext()) {
                val base = reader.toSearchResult() ?: continue
                // A recurring master's DTSTART is the series start; show its
                // nearest occurrence instead so the date is the one the user
                // actually cares about (and sorting reflects it).
                val recurring = !reader.getString(SearchProjection.IDX_RRULE).isNullOrEmpty() ||
                    !reader.getString(SearchProjection.IDX_RDATE).isNullOrEmpty()
                out += if (recurring) {
                    nearestOccurrenceMillis(base.eventId)?.let { (begin, end) ->
                        base.copy(
                            start = begin.toKotlinInstantFromEpochMillis(),
                            end = end.toKotlinInstantFromEpochMillis(),
                        )
                    } ?: base
                } else {
                    base
                }
            }
            out
        } ?: emptyList()
    }

    /**
     * The occurrence of [eventId] nearest to now: the soonest upcoming one
     * within [OCCURRENCE_WINDOW_MILLIS] ahead, else the most recent past one
     * within the same window behind. Null when neither exists (e.g. a series
     * that only starts further out than the window) — the caller then keeps the
     * series-start DTSTART. Returns (begin, end) epoch millis.
     */
    private fun nearestOccurrenceMillis(eventId: Long): Pair<Long, Long>? {
        val now = System.currentTimeMillis()
        return occurrenceInWindow(eventId, now, now + OCCURRENCE_WINDOW_MILLIS, soonestFirst = true)
            ?: occurrenceInWindow(eventId, now - OCCURRENCE_WINDOW_MILLIS, now, soonestFirst = false)
    }

    private fun occurrenceInWindow(
        eventId: Long,
        beginMillis: Long,
        endMillis: Long,
        soonestFirst: Boolean,
    ): Pair<Long, Long>? {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, beginMillis)
            ContentUris.appendId(this, endMillis)
        }.build()
        val order = CalendarContract.Instances.BEGIN + if (soonestFirst) " ASC" else " DESC"
        return resolver.query(
            uri,
            arrayOf(CalendarContract.Instances.BEGIN, CalendarContract.Instances.END),
            "${CalendarContract.Instances.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            order,
        )?.use { c ->
            if (!c.moveToFirst()) null else c.getLong(0) to c.getLong(1)
        }
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
            ?.curatedForPicker()
            ?: emptyList()
    }

    override fun exportableEvents(calendarIds: Set<Long>?): List<IcsEvent> {
        // Only the local calendars the app owns and can write — synced calendars
        // already have a backup (their server). Exclude the managed special-dates
        // mirror calendars: their events are derived from contacts, not authored
        // here, and re-materialise from the contact sync — backing them up would
        // just duplicate them on restore. A non-null [calendarIds] narrows the
        // export to the user's chosen subset. Map id → display name for the
        // X-CALENDULA-CALENDAR tag a restore uses to fan back out.
        val names = calendars()
            .filter {
                it.isLocal && it.canModifyContents && !it.isManaged &&
                    (calendarIds == null || it.id in calendarIds)
            }
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
        // A globally-unique UID so a later .ics backup/restore can identify
        // the event and not duplicate it on re-import (the provider leaves
        // this null for events it didn't sync). Older rows without one fall
        // back to a stable synthesised UID at export time (deriveIcsUid).
        val values = buildEventInsertValues(
            form,
            uid = "${UUID.randomUUID()}@calendula",
            times = form.toWriteTimes(ZoneId.systemDefault()),
        ).toContentValues().apply {
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
        seedReminders(eventId, form, allDayReminderTimeMinutes)
        // Guests are best-effort like reminders: a row that fails to attach is
        // logged, not surfaced as a failed create. Calendula never sends an
        // invitation — it only writes the rows; the backend decides delivery.
        insertAttendees(eventId, form.attendees)
        return eventId
    }

    /**
     * Attach [form]'s reminders to a freshly inserted event. Best effort
     * (spec §8): the event exists at this point — a reminder that fails to
     * attach is logged, not surfaced as a failed create.
     */
    private fun seedReminders(eventId: Long, form: EventForm, allDayReminderTimeMinutes: Int) =
        insertReminderRows(eventId, encodedReminders(form, allDayReminderTimeMinutes))

    /** Insert one `METHOD_ALERT` reminder row per raw provider offset. */
    private fun insertReminderRows(eventId: Long, encodedMinutes: List<Int>) {
        encodedMinutes.forEach { minutes ->
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

    /**
     * A managed all-day event's reminders, encoded so they fire at
     * [allDayReminderTimeMinutes] on its **upcoming** occurrence. The event's
     * DTSTART is an ancient recurrence anchor (see [nextYearlyOccurrence]); using
     * it directly would sample a stale timezone offset, so the offset is sampled
     * at the next occurrence of the anchor's month/day instead.
     */
    private fun managedAllDayReminderMinutes(
        anchor: LocalDate,
        reminders: List<Int>,
        allDayReminderTimeMinutes: Int,
    ): List<Int> {
        val zone = ZoneId.systemDefault()
        val onDate = nextYearlyOccurrence(anchor.monthValue, anchor.dayOfMonth, LocalDate.now(zone))
        return reminders
            .map { toProviderAllDayMinutes(it, onDate, zone, allDayReminderTimeMinutes) }
            .distinct()
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
        // Same untouched-set guard for guests: only reconcile when the form's
        // attendees actually changed, so unrelated edits never disturb the
        // organizer/resource rows the form doesn't model.
        if (updated.attendees.toSet() != original.attendees.toSet()) {
            reconcileAttendees(eventId, updated.attendees)
        }
    }

    override fun moveEvent(
        eventId: Long,
        targetCalendarId: Long,
        original: EventForm,
        updated: EventForm,
        allDayReminderTimeMinutes: Int,
    ): Long {
        // CALENDAR_ID can't be updated in place, so re-create the event on the
        // target calendar and delete the source. Everything that identifies or
        // hangs off the event is copied first; the source row is removed only
        // once the copy (including its exceptions) is complete — a failure
        // anywhere before that rolls the new event back, so the move is
        // all-or-nothing and never leaves a half-copied duplicate.
        val master = queryMoveMaster(eventId)
            ?: throw WriteFailedException("read event to move id=$eventId")
        // Keep the source UID so an .ics backup dedups and a sync adapter can
        // recognise the moved event; mint one only if the source carried none.
        val uid = master.uid?.takeIf { it.isNotEmpty() } ?: "${UUID.randomUUID()}@calendula"
        val newEventId = resolver.insert(
            CalendarContract.Events.CONTENT_URI,
            buildMovedMasterValues(master.event, targetCalendarId, uid).toContentValues(),
        )?.let(ContentUris::parseId)
            ?: throw WriteFailedException("insert moved event into calendar id=$targetCalendarId")
        try {
            copyReminderRows(fromEventId = eventId, toEventId = newEventId)
            insertAttendees(newEventId, editableAttendees(eventId))
            copyExceptions(fromEventId = eventId, toEventId = newEventId)
            // Apply the user's field edits exactly as a same-calendar "all
            // events" save would: the moved master carries the source's values,
            // so the dirty diff against [original] writes only what changed
            // (including a time or rrule edit made in the same save).
            updateEvent(newEventId, original, updated, allDayReminderTimeMinutes)
        } catch (t: Throwable) {
            // Undo the partial copy so a failed move leaves nothing behind; the
            // source is still intact (it's deleted only past this point).
            runCatching { deleteEvent(newEventId) }
            throw t
        }
        deleteEvent(eventId)
        return newEventId
    }

    /** The master row of the event to move, as a verbatim-insert snapshot. */
    private fun queryMoveMaster(eventId: Long): MoveMaster? = resolver.query(
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
        MoveMasterProjection.COLUMNS,
        null, null, null,
    )?.use { c ->
        if (!c.moveToFirst()) return@use null
        val r = CursorColumnReader(c)
        MoveMaster(
            uid = r.getString(MoveMasterProjection.IDX_UID),
            event = MasterEventSnapshot(
                title = r.getString(MoveMasterProjection.IDX_TITLE).orEmpty(),
                isAllDay = r.getInt(MoveMasterProjection.IDX_ALL_DAY) != 0,
                dtStartMillis = r.getLong(MoveMasterProjection.IDX_DTSTART),
                dtEndMillis = r.getLong(MoveMasterProjection.IDX_DTEND)
                    .takeUnless { r.isNull(MoveMasterProjection.IDX_DTEND) },
                duration = r.getString(MoveMasterProjection.IDX_DURATION),
                rrule = r.getString(MoveMasterProjection.IDX_RRULE)?.takeIf { it.isNotBlank() },
                rdate = r.getString(MoveMasterProjection.IDX_RDATE),
                exdate = r.getString(MoveMasterProjection.IDX_EXDATE),
                timezone = r.getString(MoveMasterProjection.IDX_EVENT_TIMEZONE),
                availability = r.getInt(MoveMasterProjection.IDX_AVAILABILITY),
                accessLevel = r.getInt(MoveMasterProjection.IDX_ACCESS_LEVEL),
                status = r.getInt(MoveMasterProjection.IDX_STATUS)
                    .takeUnless { r.isNull(MoveMasterProjection.IDX_STATUS) },
                location = r.getString(MoveMasterProjection.IDX_LOCATION),
                description = r.getString(MoveMasterProjection.IDX_DESCRIPTION),
            ),
        )
    }

    private data class MoveMaster(val uid: String?, val event: MasterEventSnapshot)

    /** Copy every stored reminder row (raw offsets and method) onto [toEventId]. */
    private fun copyReminderRows(fromEventId: Long, toEventId: Long) = resolver.query(
        CalendarContract.Reminders.CONTENT_URI,
        arrayOf(CalendarContract.Reminders.MINUTES, CalendarContract.Reminders.METHOD),
        CalendarContract.Reminders.EVENT_ID + " = ?",
        arrayOf(fromEventId.toString()),
        null,
    )?.use { c ->
        while (c.moveToNext()) {
            val values = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, toEventId)
                put(CalendarContract.Reminders.MINUTES, c.getInt(0))
                put(CalendarContract.Reminders.METHOD, c.getInt(1))
            }
            if (resolver.insert(CalendarContract.Reminders.CONTENT_URI, values) == null) {
                Log.w(TAG, "Failed to copy a reminder to moved event $toEventId")
            }
        }
    } ?: Unit

    /**
     * The event's editable guests as [EventAttendee]s — the rows the move
     * re-creates. Mirrors the edit form's filter: the organizer and resource
     * rows (backend-owned, not user-editable) and any without an email are
     * dropped; the response status resets to "invited" on re-insert.
     */
    private fun editableAttendees(eventId: Long): List<EventAttendee> = queryAttendees(eventId)
        .filter {
            it.relationship != AttendeeRelationship.Organizer && it.type != AttendeeType.Resource
        }
        .mapNotNull { a ->
            a.email?.takeIf { it.isNotBlank() }?.let { email ->
                EventAttendee(email = email, name = a.name, optional = a.type == AttendeeType.Optional)
            }
        }

    /**
     * Replay every exception of the series [fromEventId] against the moved series
     * [toEventId]. The copy preserved the recurrence skeleton, so each occurrence
     * time still resolves: a cancelled occurrence is re-hidden with a cancelled
     * exception, a modified one is re-inserted with its fields (then its reminders
     * reconciled and editable guests copied). No-op for a non-recurring event.
     */
    private fun copyExceptions(fromEventId: Long, toEventId: Long) {
        queryExceptionRows(fromEventId).forEach { ex ->
            if (ex.isCancelled) {
                val values = ContentValues().apply {
                    put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, ex.originalInstanceMillis)
                    put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
                }
                resolver.insert(
                    ContentUris.withAppendedId(
                        CalendarContract.Events.CONTENT_EXCEPTION_URI, toEventId,
                    ),
                    values,
                ) ?: throw WriteFailedException("copy cancelled occurrence to event id=$toEventId")
            } else {
                val uri = resolver.insert(
                    ContentUris.withAppendedId(
                        CalendarContract.Events.CONTENT_EXCEPTION_URI, toEventId,
                    ),
                    buildCopiedExceptionValues(ex).toContentValues(),
                ) ?: throw WriteFailedException("copy modified occurrence to event id=$toEventId")
                val newExceptionId = ContentUris.parseId(uri)
                // The provider may clone the parent's reminders onto the new
                // exception; reconcile to the source exception's exact set so
                // they neither double nor drop. Same DTSTART → same all-day
                // encoding, so the stored offsets match directly.
                reconcileReminders(
                    newExceptionId,
                    queryReminders(ex.exceptionEventId).map { it.minutes },
                )
                insertAttendees(newExceptionId, editableAttendees(ex.exceptionEventId))
            }
        }
    }

    /** The series' exception rows (modified + cancelled), oldest occurrence first. */
    private fun queryExceptionRows(seriesEventId: Long): List<ExceptionRowSnapshot> = resolver.query(
        CalendarContract.Events.CONTENT_URI,
        ExceptionProjection.COLUMNS,
        "${CalendarContract.Events.ORIGINAL_ID} = ? AND ${CalendarContract.Events.DELETED} = 0",
        arrayOf(seriesEventId.toString()),
        CalendarContract.Events.ORIGINAL_INSTANCE_TIME + " ASC",
    )?.use { c ->
        c.mapAll {
            val r = CursorColumnReader(c)
            val status = r.getInt(ExceptionProjection.IDX_STATUS)
                .takeUnless { r.isNull(ExceptionProjection.IDX_STATUS) }
            ExceptionRowSnapshot(
                exceptionEventId = r.getLong(ExceptionProjection.IDX_ID),
                originalInstanceMillis = r.getLong(ExceptionProjection.IDX_ORIGINAL_INSTANCE_TIME),
                isCancelled = status == CalendarContract.Events.STATUS_CANCELED,
                status = status,
                title = r.getString(ExceptionProjection.IDX_TITLE).orEmpty(),
                isAllDay = r.getInt(ExceptionProjection.IDX_ALL_DAY) != 0,
                dtStartMillis = r.getLong(ExceptionProjection.IDX_DTSTART),
                dtEndMillis = r.getLong(ExceptionProjection.IDX_DTEND)
                    .takeUnless { r.isNull(ExceptionProjection.IDX_DTEND) },
                duration = r.getString(ExceptionProjection.IDX_DURATION),
                timezone = r.getString(ExceptionProjection.IDX_EVENT_TIMEZONE),
                availability = r.getInt(ExceptionProjection.IDX_AVAILABILITY),
                accessLevel = r.getInt(ExceptionProjection.IDX_ACCESS_LEVEL),
                location = r.getString(ExceptionProjection.IDX_LOCATION),
                description = r.getString(ExceptionProjection.IDX_DESCRIPTION),
            )
        }
    } ?: emptyList()

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
        reconcileAttendees(exceptionId, form.attendees)
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
            CalendarContract.Events._SYNC_ID,
            CalendarContract.Events.EXDATE,
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
                syncId = c.getString(5),
                exdate = c.getString(6),
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
        /** Null on a local calendar (and before a synced event's first push). */
        val syncId: String? = null,
        val exdate: String? = null,
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

    /** Normalised key an attendee is matched/deduped on (case-insensitive email). */
    private fun EventAttendee.key(): String = email.trim().lowercase()

    /** Distinct guests by email; blanks dropped (we key and write on the address). */
    private fun List<EventAttendee>.distinctByEmail(): List<EventAttendee> {
        val seen = HashSet<String>()
        return filter { it.email.isNotBlank() && seen.add(it.key()) }
    }

    private fun attendeeValues(eventId: Long, attendee: EventAttendee): ContentValues =
        ContentValues().apply {
            put(CalendarContract.Attendees.EVENT_ID, eventId)
            put(CalendarContract.Attendees.ATTENDEE_EMAIL, attendee.email.trim())
            attendee.name.trim().takeIf { it.isNotEmpty() }
                ?.let { put(CalendarContract.Attendees.ATTENDEE_NAME, it) }
            // A plain guest — never the organizer (we don't fabricate scheduling
            // identity; the backend owns that). Invited = awaiting their reply.
            put(
                CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
                CalendarContract.Attendees.RELATIONSHIP_ATTENDEE,
            )
            put(CalendarContract.Attendees.ATTENDEE_TYPE, attendee.providerType())
            put(
                CalendarContract.Attendees.ATTENDEE_STATUS,
                CalendarContract.Attendees.ATTENDEE_STATUS_INVITED,
            )
        }

    private fun EventAttendee.providerType(): Int = if (optional) {
        CalendarContract.Attendees.TYPE_OPTIONAL
    } else {
        CalendarContract.Attendees.TYPE_REQUIRED
    }

    /** Best-effort attendee inserts (like reminders); emails are never logged. */
    private fun insertAttendees(eventId: Long, attendees: List<EventAttendee>) {
        attendees.distinctByEmail().forEach { attendee ->
            if (resolver.insert(
                    CalendarContract.Attendees.CONTENT_URI,
                    attendeeValues(eventId, attendee),
                ) == null
            ) {
                Log.w(TAG, "Failed to attach a guest to event $eventId")
            }
        }
    }

    private data class AttendeeRow(
        val id: Long,
        val email: String,
        val relationship: Int,
        val type: Int,
    ) {
        val key: String get() = email.trim().lowercase()
    }

    private fun queryAttendeeRows(eventId: Long): List<AttendeeRow> = resolver.query(
        CalendarContract.Attendees.CONTENT_URI,
        arrayOf(
            CalendarContract.Attendees._ID,
            CalendarContract.Attendees.ATTENDEE_EMAIL,
            CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
            CalendarContract.Attendees.ATTENDEE_TYPE,
        ),
        CalendarContract.Attendees.EVENT_ID + " = ?",
        arrayOf(eventId.toString()),
        null,
    )?.use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    AttendeeRow(
                        id = c.getLong(0),
                        email = c.getString(1).orEmpty(),
                        relationship = c.getInt(2),
                        type = c.getInt(3),
                    ),
                )
            }
        }
    } ?: emptyList()

    /**
     * Make the event's *editable* guest rows match [attendees]. Only rows we
     * own — has an email, not the organizer, not a resource — are reconciled;
     * everything else the backend put there is left untouched. Matched rows keep
     * their response status (only the required/optional type is updated if it
     * changed); dropped rows are deleted; new guests inserted. Calendula sends
     * nothing — the backend decides if anyone is notified on sync.
     */
    private fun reconcileAttendees(eventId: Long, attendees: List<EventAttendee>) {
        val target = attendees.distinctByEmail()
        val targetByKey = target.associateBy { it.key() }
        val editable = queryAttendeeRows(eventId).filter {
            it.email.isNotBlank() &&
                it.relationship != CalendarContract.Attendees.RELATIONSHIP_ORGANIZER &&
                it.type != CalendarContract.Attendees.TYPE_RESOURCE
        }
        val editableKeys = editable.map { it.key }.toSet()
        editable.forEach { row ->
            val want = targetByKey[row.key]
            if (want == null) {
                resolver.delete(
                    CalendarContract.Attendees.CONTENT_URI,
                    CalendarContract.Attendees._ID + " = ?",
                    arrayOf(row.id.toString()),
                )
            } else if (row.type != want.providerType()) {
                resolver.update(
                    CalendarContract.Attendees.CONTENT_URI,
                    ContentValues().apply {
                        put(CalendarContract.Attendees.ATTENDEE_TYPE, want.providerType())
                    },
                    CalendarContract.Attendees._ID + " = ?",
                    arrayOf(row.id.toString()),
                )
            }
        }
        target.filterNot { it.key() in editableKeys }.forEach { attendee ->
            if (resolver.insert(
                    CalendarContract.Attendees.CONTENT_URI,
                    attendeeValues(eventId, attendee),
                ) == null
            ) {
                Log.w(TAG, "Failed to attach a guest to event $eventId")
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
        val row = querySeriesRow(eventId)
        if (row.syncId == null) {
            // No _sync_id — a local calendar, or a synced event not pushed yet.
            // A cancelled exception can only attach to its parent through
            // ORIGINAL_SYNC_ID, so with none the link never forms and the
            // provider's expansion of the *parent* collapses, taking every other
            // occurrence with it (#47 on a local calendar). EXDATE needs no link.
            // Calendula's own contact special-date calendars are local and hold
            // yearly series, so this path is reached in normal use.
            val values = buildOccurrenceExdateValues(
                existingExdate = row.exdate,
                occurrenceMillis = beginMillis,
                dtStartMillis = row.dtStartMillis,
                rrule = row.rrule,
                duration = row.duration,
                timezone = row.timezone,
                allDay = row.allDay,
            )
            val updated = resolver.update(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                values.toContentValues(), null, null,
            )
            if (updated == 0) {
                throw WriteFailedException("exdate occurrence event id=$eventId begin=$beginMillis")
            }
            return
        }
        // A cancelled exception row hides exactly this occurrence; the sync
        // adapter turns it into an EXDATE/cancelled VEVENT upstream. It carries
        // the full time set (DTSTART + DURATION + zone) so the provider derives a
        // single instance rather than cloning the master's RRULE — the same trap
        // the edit path documents (Codeberg #16).
        val values = buildOccurrenceCancelValues(
            originalInstanceMillis = beginMillis,
            dtStartMillis = beginMillis,
            duration = row.duration,
            timezone = row.timezone,
            allDay = row.allDay,
        )
        val uri = ContentUris.withAppendedId(
            CalendarContract.Events.CONTENT_EXCEPTION_URI, eventId,
        )
        resolver.insert(uri, values.toContentValues())
            ?: throw WriteFailedException("cancel occurrence event id=$eventId begin=$beginMillis")
    }

    override fun registerChangeListener(listener: () -> Unit) {
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                listener()
            }
        }
        synchronized(observerLock) {
            observers[listener] = obs
            // Attach now if we already hold the permission; otherwise it stays
            // pending and re-attaches on the first read after the grant.
            registerObserverLocked(obs)
        }
    }

    override fun unregisterChangeListener(listener: () -> Unit) {
        synchronized(observerLock) {
            observers.remove(listener)?.let { obs ->
                if (registeredObservers.remove(obs)) resolver.unregisterContentObserver(obs)
            }
        }
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

    /** The `CAL_SYNC2` marker value identifying a managed calendar's [type]. */
    private fun markerFor(type: SpecialDateType): String = when (type) {
        SpecialDateType.Birthday -> "$MANAGED_MARKER_PREFIX:birthday"
        SpecialDateType.Anniversary -> "$MANAGED_MARKER_PREFIX:anniversary"
        SpecialDateType.Custom -> "$MANAGED_MARKER_PREFIX:custom"
    }

    /** Inverse of [markerFor]; null for a value that isn't one of ours. */
    private fun typeForMarker(marker: String?): SpecialDateType? =
        SpecialDateType.entries.firstOrNull { markerFor(it) == marker }

    private companion object {
        const val TAG = "CalendarDataSource"

        /**
         * Shared account for every app-created local calendar, so they group
         * together (by account) in the filter sheet and calendar manager.
         */
        const val LOCAL_ACCOUNT_NAME = "Calendula"

        /**
         * Column and namespace for the special-dates marker (shared with the
         * calendar projection, which reads it into [CalendarSource.isManaged]).
         * CAL_SYNC1 already holds the local-calendar description, so it uses CAL_SYNC2.
         */
        val MANAGED_MARKER_COLUMN: String = CalendarProjection.MANAGED_MARKER_COLUMN
        const val MANAGED_MARKER_PREFIX = CalendarProjection.MANAGED_MARKER_PREFIX

        /**
         * How far ahead/behind a search looks for a recurring event's nearest
         * occurrence (~2 years). Wide enough for everyday series; a series that
         * next fires beyond it falls back to its series-start date.
         */
        const val OCCURRENCE_WINDOW_MILLIS = 2L * 365 * 24 * 60 * 60 * 1000
    }
}
