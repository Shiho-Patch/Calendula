package de.jeanlucmakiola.calendula.data.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.domain.calendarVisibilityPlan
import de.jeanlucmakiola.calendula.domain.hasSystemHiddenCalendars
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the app's pending "switched off" set (see
 * [CalendarPrefs.pendingDisabledCalendarIds]) and the system's
 * `Calendars.VISIBLE` in step — the fold-in of the retired app-local visibility
 * model (#75), and the standing drain for switch-offs made without
 * `WRITE_CALENDAR`.
 *
 * Runs on every launch, and again the moment the permission screen grants the
 * calendar permissions. It is a no-op whenever the pending set is empty, which
 * is the steady state: each entry is written and dropped individually, so a run
 * that dies part-way resumes exactly where it stopped and never re-applies a
 * write the user has since undone by hand.
 *
 * The reconciliation only hides (see [calendarVisibilityPlan]). Calendars hidden
 * at system level stay hidden, and the first run that sees one arms the one-time
 * notice explaining why Calendula no longer lists their events.
 */
@Singleton
class CalendarVisibilityReconciler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataSource: CalendarDataSource,
    private val prefs: CalendarPrefs,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun run() = withContext(io) {
        // Everything, the DataStore reads included, sits inside the guard: this
        // runs in a bare application-scope coroutine with no exception handler,
        // so an IOException from a damaged preferences file would otherwise take
        // the process down on every launch.
        try {
            if (!hasPermission(Manifest.permission.READ_CALENDAR)) return@withContext
            val pending = prefs.pendingDisabledCalendarIds.first()
            val calendars = dataSource.calendars()
            armNoticeOnce(calendars, pending)
            if (pending.isEmpty() || !hasPermission(Manifest.permission.WRITE_CALENDAR)) {
                return@withContext
            }
            val plan = calendarVisibilityPlan(calendars, pending)
            // Already off, or gone from the device — nothing to write, so let
            // those ids leave the pending set with the rest.
            prefs.removePendingDisabledCalendarIds(plan.settled)
            // One calendar per write: the provider skips its reminder-alarm
            // reschedule for anything but a single-id update (see
            // [CalendarDataSource.setCalendarVisible]). Dropping each id as it
            // lands keeps a part-applied run resumable.
            for (id in plan.hide) {
                dataSource.setCalendarVisible(id, false)
                prefs.removePendingDisabledCalendarIds(setOf(id))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Calendar visibility reconcile failed; will retry", e)
        }
    }

    /**
     * Arm the one-time notice if the device holds a calendar switched off
     * outside Calendula. Evaluated once, on the first run that can read the
     * calendars at all; the answer — including "nothing to say" — is stored, so
     * the notice can't resurface later, when the same state would no longer be
     * news to the user.
     */
    private suspend fun armNoticeOnce(calendars: List<CalendarSource>, pending: Set<Long>) {
        if (prefs.visibilityNoticePending.first() != null) return
        prefs.setVisibilityNoticePending(hasSystemHiddenCalendars(calendars, pending))
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Lets non-injectable entry points (the Application) reach the reconciler. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun calendarVisibilityReconciler(): CalendarVisibilityReconciler
    }

    private companion object {
        const val TAG = "CalendarVisibility"
    }
}
