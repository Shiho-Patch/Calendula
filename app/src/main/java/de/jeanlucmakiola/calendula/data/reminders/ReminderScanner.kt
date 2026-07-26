package de.jeanlucmakiola.calendula.data.reminders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.jeanlucmakiola.calendula.data.calendar.CalendarDataSource
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.data.prefs.ReminderStatePrefs
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.domain.reminders.planReminders
import de.jeanlucmakiola.calendula.domain.reminders.reminderQueryHorizon
import de.jeanlucmakiola.calendula.domain.reminders.reminderWatermark
import de.jeanlucmakiola.calendula.domain.reminders.scheduleReminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One pass of in-house reminder delivery: read what is planned, post what has
 * come due, and arm the next wake-up.
 *
 * This is the whole loop. Every trigger — the alarm firing, boot, a clock or
 * timezone change, an edit landing in the provider, the app starting, the daily
 * safety net — runs the same [scan], so there is a single path to reason about
 * and no ordering between triggers to get wrong. Re-running it is always safe:
 * the watermark in [ReminderStatePrefs] decides what is owed, not the trigger.
 */
@Singleton
class ReminderScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val source: ReminderInstanceSource,
    private val calendarDataSource: CalendarDataSource,
    private val notifier: ReminderNotifier,
    private val alarms: ReminderAlarmScheduler,
    private val state: ReminderStatePrefs,
    private val settingsPrefs: SettingsPrefs,
    @IoDispatcher private val io: kotlinx.coroutines.CoroutineDispatcher,
) {

    // Triggers overlap freely (an alarm during a burst of edits); serialize so
    // two passes can't both read the same watermark and post the same reminder.
    private val scanLock = Mutex()

    private val scope = CoroutineScope(SupervisorJob() + io)
    private val providerChanges = MutableSharedFlow<Unit>(
        replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var watching = false

    suspend fun scan() = withContext(io) {
        scanLock.withLock {
            try {
                runScan()
            } catch (e: SecurityException) {
                // The calendar permission was revoked mid-flight. Nothing to
                // re-arm and nothing to recover from — the next grant re-scans.
                Log.w(TAG, "Reminder scan lacks the calendar permission", e)
            } catch (e: Exception) {
                Log.w(TAG, "Reminder scan failed", e)
            }
        }
    }

    private suspend fun runScan() {
        val now = System.currentTimeMillis()
        if (!hasReadCalendar()) return

        if (!settingsPrefs.remindersEnabled.first()) {
            // Reminders off: drop the wake-up, but keep the watermark moving so
            // switching them back on doesn't replay everything missed meanwhile.
            alarms.cancelScan()
            state.setLastScanMillis(now)
            return
        }

        val lookahead = reminderQueryHorizon(LOOKAHEAD_MILLIS, source.longestReminderMinutes())
        // Reach a little into the past as well: an event already under way can
        // still owe a reminder (an all-day "at time of event" encodes to a
        // negative offset, which fires after begin), and a catch-up pass needs
        // to see the occurrences whose moment it missed.
        val occurrences = source.occurrences(now - PAST_WINDOW_MILLIS, now + lookahead)
        val planned = planReminders(
            instances = occurrences,
            minutesByEvent = source.reminderMinutes(occurrences.map { it.eventId }),
            zone = ZoneId.systemDefault(),
            allDayTimeMinutes = settingsPrefs.allDayReminderTimeMinutes.first(),
        )

        val schedule = scheduleReminders(
            planned = planned,
            lastFiredMillis = reminderWatermark(state.lastScanMillis(), now),
            nowMillis = now,
            horizonMillis = now + MAX_ALARM_INTERVAL_MILLIS,
        )

        if (notifier.canPost()) {
            schedule.due.forEach { notifier.post(it.toAlert()) }
        }
        // Advance regardless of whether anything could be posted: a user who
        // muted notifications is not owed a backlog when they unmute.
        state.setLastScanMillis(now)
        alarms.scheduleScan(schedule.nextAlarmMillis)
    }

    private fun hasReadCalendar(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CALENDAR,
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Re-scan when the provider changes, so an event saved or deleted in the app
     * re-arms the alarm immediately rather than waiting for the next pass.
     *
     * Debounced: a single save writes the event, its reminders and its
     * attendees, and each lands as its own notification. Only useful while the
     * process is alive — every other trigger covers the rest, which is why
     * nothing here needs to survive it.
     */
    fun startWatchingProvider() {
        if (watching) return
        watching = true
        providerChanges
            .debounce(PROVIDER_CHANGE_DEBOUNCE_MILLIS)
            .onEach { scan() }
            .launchIn(scope)
        calendarDataSource.registerChangeListener { providerChanges.tryEmit(Unit) }
    }

    /** Fire-and-forget scan for callers that are not in a coroutine already. */
    fun scanInBackground() {
        scope.launch(start = CoroutineStart.DEFAULT) { scan() }
    }

    private companion object {
        const val TAG = "ReminderScanner"

        /**
         * How far ahead occurrences are read. Stretched further by the longest
         * reminder offset in the table, so this is only the floor.
         */
        const val LOOKAHEAD_MILLIS = 7L * 24 * 60 * 60 * 1000

        /** How far back to look for occurrences that may still owe a reminder. */
        const val PAST_WINDOW_MILLIS = 24L * 60 * 60 * 1000

        /**
         * Never wait longer than a day for the next pass, even with nothing
         * pending: it rolls the lookahead window forward and re-arms an alarm
         * the system may have dropped.
         */
        const val MAX_ALARM_INTERVAL_MILLIS = 24L * 60 * 60 * 1000

        const val PROVIDER_CHANGE_DEBOUNCE_MILLIS = 2_000L
    }
}
