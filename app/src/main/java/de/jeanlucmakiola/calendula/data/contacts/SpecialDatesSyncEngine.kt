package de.jeanlucmakiola.calendula.data.contacts

import android.provider.CalendarContract
import de.jeanlucmakiola.calendula.data.calendar.CalendarDataSource
import de.jeanlucmakiola.calendula.data.calendar.ManagedEventRow
import de.jeanlucmakiola.calendula.data.calendar.toWriteTimes
import de.jeanlucmakiola.calendula.data.prefs.CalendarReminderOverride
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.domain.Availability
import de.jeanlucmakiola.calendula.domain.EventForm
import de.jeanlucmakiola.calendula.domain.RecurrenceFreq
import de.jeanlucmakiola.calendula.domain.SimpleRecurrence
import de.jeanlucmakiola.calendula.domain.contacts.ContactSpecialDate
import de.jeanlucmakiola.calendula.domain.contacts.SpecialDateType
import de.jeanlucmakiola.calendula.domain.contacts.anchorDate
import de.jeanlucmakiola.calendula.domain.contacts.managedUid
import de.jeanlucmakiola.calendula.domain.contacts.renderSpecialDateTitle
import de.jeanlucmakiola.calendula.domain.toRRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of one mirror reconcile, reported back so the worker can record status. */
enum class SpecialDatesSyncResult { Success, Disabled, PermissionMissing }

/**
 * Localized, per-type presentation the engine needs but can't derive purely
 * (calendar display name, colour, and the default title template). Kept behind
 * an interface so the engine's diff stays unit-testable without Android
 * resources.
 */
interface SpecialDatesCalendarSpec {
    fun displayName(type: SpecialDateType): String
    fun color(type: SpecialDateType): Int
    fun defaultTitleTemplate(type: SpecialDateType): String
}

/**
 * The stored title template for [type], falling back to the localized default
 * when blank/absent — the one resolution rule shared by the sync (what gets
 * written) and the settings editor (what gets shown).
 */
fun SpecialDatesCalendarSpec.resolveTitleTemplate(
    type: SpecialDateType,
    stored: Map<SpecialDateType, String>,
): String = stored[type]?.takeIf { it.isNotBlank() } ?: defaultTitleTemplate(type)

/**
 * Mirrors contact birthdays/anniversaries/custom dates into local calendars,
 * one per type. Every reconcile is an idempotent diff keyed on the deterministic
 * `UID_2445` ([managedEventUid]): new contacts are inserted (seeding user-owned
 * fields once), changed contacts get a *targeted* managed-column update, and
 * removed contacts are deleted — so a user's own edits (reminders, location,
 * notes) are never clobbered. See docs/design/contact-special-dates.md.
 */
@Singleton
class SpecialDatesSyncEngine @Inject constructor(
    private val contacts: ContactSpecialDatesDataSource,
    private val calendars: CalendarDataSource,
    private val prefs: SettingsPrefs,
    private val spec: SpecialDatesCalendarSpec,
) {

    // Serializes calendar-lifecycle work: two overlapping syncs (the daily job
    // racing a "Sync now"/foreground run) would each see no managed calendar and
    // both create one; a teardown racing an in-flight sync would delete calendars
    // the sync then recreates. Holding this for the whole of sync()/teardown()
    // makes those check-then-act sequences atomic, and — because sync() re-reads
    // the enabled flag inside the lock — a teardown always wins the race.
    private val lifecycleMutex = Mutex()

    /**
     * Reconcile every enabled type against the device's contacts. Returns why it
     * stopped early (disabled / permission gone) or [SpecialDatesSyncResult.Success].
     */
    suspend fun sync(): SpecialDatesSyncResult = lifecycleMutex.withLock {
        if (!prefs.specialDatesEnabled.first()) return SpecialDatesSyncResult.Disabled
        if (!contacts.hasPermission()) return SpecialDatesSyncResult.PermissionMissing

        val enabledTypes = prefs.specialDatesTypes.first()
        val calendarByType = reconcileCalendars(enabledTypes)

        val desiredByType = contacts.readSpecialDates().groupBy { it.type }
        val reminderCtx = readReminderContext()
        val templates = prefs.specialDatesTitleTemplates.first()
        val showYear = prefs.specialDatesShowYear.first()

        calendarByType.forEach { (type, calendarId) ->
            val template = spec.resolveTitleTemplate(type, templates)
            syncType(
                calendarId = calendarId,
                type = type,
                contactsOfType = desiredByType[type].orEmpty(),
                template = template,
                showYear = showYear,
                reminderCtx = reminderCtx,
            )
        }
        SpecialDatesSyncResult.Success
    }

    /**
     * Set the reminder default for a type's managed calendar and apply it to
     * **all** its existing events too (not just future ones) — for managed
     * calendars the reminder is a calendar-level setting. Persists the per-calendar
     * all-day override so new events keep matching. No-op if the calendar for
     * [type] doesn't exist yet.
     */
    suspend fun applyReminders(type: SpecialDateType, override: CalendarReminderOverride) {
        val calendarId = prefs.specialDatesCalendars.first()[type] ?: return
        prefs.setCalendarAllDayReminderOverride(calendarId, override)
        val minutes = when (override) {
            CalendarReminderOverride.Inherit -> prefs.defaultAllDayReminderMinutes.first()
            CalendarReminderOverride.None -> emptyList()
            is CalendarReminderOverride.Minutes -> override.minutes
        }
        calendars.applyManagedCalendarReminders(
            calendarId = calendarId,
            allDayReminderTimeMinutes = prefs.allDayReminderTimeMinutes.first(),
            minutes = minutes,
        )
    }

    /** Delete every managed calendar and forget its id — used when the feature is turned off. */
    suspend fun teardown() = lifecycleMutex.withLock {
        calendars.findManagedCalendars().forEach { calendars.deleteCalendar(it.id) }
        SpecialDateType.entries.forEach { prefs.setSpecialDatesCalendarId(it, null) }
    }

    /**
     * Ensure each enabled type has exactly one managed calendar (adopting an
     * existing one, or the stored id if it still exists, else creating one) and
     * that disabled types have none. Returns the calendar id per enabled type.
     */
    private suspend fun reconcileCalendars(enabledTypes: Set<SpecialDateType>): Map<SpecialDateType, Long> {
        val foundByType = calendars.findManagedCalendars()
            .groupBy({ it.type }, { it.id })
            .mapValues { it.value.first() }
        val stored = prefs.specialDatesCalendars.first()
        val result = LinkedHashMap<SpecialDateType, Long>()
        for (type in SpecialDateType.entries) {
            // A stored id counts only if the calendar still exists (the user may
            // have deleted it in system settings); otherwise adopt a found one.
            val existingId = stored[type]?.takeIf { id -> foundByType.containsValue(id) }
                ?: foundByType[type]
            if (type in enabledTypes) {
                val id = existingId ?: createCalendar(type)
                if (stored[type] != id) prefs.setSpecialDatesCalendarId(type, id)
                result[type] = id
            } else {
                if (existingId != null) calendars.deleteCalendar(existingId)
                if (stored.containsKey(type)) prefs.setSpecialDatesCalendarId(type, null)
            }
        }
        return result
    }

    private suspend fun createCalendar(type: SpecialDateType): Long {
        // reconcileCalendars persists the id — the single place ids are recorded.
        val id = calendars.createManagedCalendar(spec.displayName(type), spec.color(type), type)
        // Seed a useful all-day reminder default (on the day + a week before), so
        // birthdays get lead time out of the box. Per-calendar, user-adjustable —
        // only set when the user hasn't already configured this calendar.
        if (!prefs.perCalendarAllDayReminderOverride.first().containsKey(id)) {
            prefs.setCalendarAllDayReminderOverride(
                id,
                CalendarReminderOverride.Minutes(DEFAULT_REMINDER_MINUTES),
            )
        }
        return id
    }

    private suspend fun syncType(
        calendarId: Long,
        type: SpecialDateType,
        contactsOfType: List<ContactSpecialDate>,
        template: String,
        showYear: Boolean,
        reminderCtx: ReminderContext,
    ) {
        val built = contactsOfType.map { sd ->
            buildManagedEvent(calendarId, type, sd, template, showYear, reminderCtx)
        }
        val diff = diffManagedEvents(built.map { it.desired }, calendars.queryManagedEvents(calendarId))
        val formByUid = built.associate { it.desired.uid to it.form }
        diff.insertUids.forEach { uid ->
            calendars.insertManagedEvent(formByUid.getValue(uid), uid, reminderCtx.allDayTimeMinutes)
        }
        diff.updates.forEach { calendars.updateManagedFields(it.eventId, it.columns) }
        diff.deleteEventIds.forEach { calendars.deleteEvent(it) }
    }

    private fun buildManagedEvent(
        calendarId: Long,
        type: SpecialDateType,
        sd: ContactSpecialDate,
        template: String,
        showYear: Boolean,
        reminderCtx: ReminderContext,
    ): BuiltManagedEvent {
        val anchor = sd.anchorDate()
        val start = LocalDateTime(anchor, LocalTime(0, 0))
        // The source year is static and correct on every occurrence (unlike age).
        val year = if (showYear) sd.year else null
        val title = renderSpecialDateTitle(template, sd.displayName, year)
        val form = EventForm(
            calendarId = calendarId,
            title = title,
            isAllDay = true,
            start = start,
            end = start,
            reminders = reminderCtx.resolveAllDay(calendarId),
            availability = Availability.Free,
            rrule = YEARLY_RRULE,
        )
        val dtStartMillis = form.toWriteTimes(ZoneId.systemDefault()).dtStartMillis
        return BuiltManagedEvent(
            desired = ManagedEventDesired(
                uid = sd.managedUid(),
                title = title,
                dtStartMillis = dtStartMillis,
                rrule = YEARLY_RRULE,
            ),
            form = form,
        )
    }

    // Managed events are always all-day, so only the all-day defaults apply.
    private suspend fun readReminderContext(): ReminderContext = ReminderContext(
        allDayGlobal = prefs.defaultAllDayReminderMinutes.first(),
        allDayOverrides = prefs.perCalendarAllDayReminderOverride.first(),
        allDayTimeMinutes = prefs.allDayReminderTimeMinutes.first(),
    )

    private data class ReminderContext(
        val allDayGlobal: List<Int>,
        val allDayOverrides: Map<Long, List<Int>>,
        val allDayTimeMinutes: Int,
    ) {
        /** Same semantics as `resolveDefaultReminder`: present-empty = explicit none. */
        fun resolveAllDay(calendarId: Long): List<Int> =
            allDayOverrides[calendarId] ?: allDayGlobal
    }

    private data class BuiltManagedEvent(val desired: ManagedEventDesired, val form: EventForm)

    private companion object {
        /** "On the day" (0) + one week before (7 days), as all-day lead minutes. */
        val DEFAULT_REMINDER_MINUTES = listOf(0, 7 * 24 * 60)
        val YEARLY_RRULE = SimpleRecurrence(freq = RecurrenceFreq.Yearly).toRRule()
    }
}

/** The managed columns of a desired event, compared against the existing row. */
internal data class ManagedEventDesired(
    val uid: String,
    val title: String,
    val dtStartMillis: Long,
    val rrule: String?,
)

internal data class ManagedFieldUpdate(val eventId: Long, val columns: Map<String, Any?>)

internal data class ManagedDiff(
    val insertUids: List<String>,
    val updates: List<ManagedFieldUpdate>,
    val deleteEventIds: List<Long>,
)

/**
 * The idempotent diff at the heart of the mirror, keyed on `UID_2445`:
 * desired-not-existing → insert, existing-not-desired → delete, and for events
 * in both only the *changed* managed columns (title/dtstart/rrule) are emitted —
 * so a re-run with no contact changes produces nothing, and a managed update
 * never touches user-owned columns or reminder rows. Pure, so it's unit-tested.
 */
internal fun diffManagedEvents(
    desired: List<ManagedEventDesired>,
    existing: List<ManagedEventRow>,
): ManagedDiff {
    val desiredByUid = desired.associateBy { it.uid }
    val existingByUid = existing.associateBy { it.uid }
    val insertUids = desired.filter { it.uid !in existingByUid }.map { it.uid }
    val deleteEventIds = existing.filter { it.uid !in desiredByUid }.map { it.eventId }
    val updates = buildList {
        for (d in desired) {
            val row = existingByUid[d.uid] ?: continue
            val columns = buildMap<String, Any?> {
                if (row.title != d.title) put(CalendarContract.Events.TITLE, d.title)
                if (row.dtStartMillis != d.dtStartMillis) {
                    put(CalendarContract.Events.DTSTART, d.dtStartMillis)
                }
                if (row.rrule != d.rrule) put(CalendarContract.Events.RRULE, d.rrule)
            }
            if (columns.isNotEmpty()) add(ManagedFieldUpdate(row.eventId, columns))
        }
    }
    return ManagedDiff(insertUids, updates, deleteEventIds)
}
