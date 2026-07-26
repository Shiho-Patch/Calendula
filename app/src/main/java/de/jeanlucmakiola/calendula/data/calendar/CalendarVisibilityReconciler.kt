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
 * Runs on every launch, and again whenever the app comes up holding the calendar
 * permission — a grant made on Android's own app-settings screen never reaches
 * the permission screen's callback. It is a no-op whenever the pending set is
 * empty and the notice has been settled, which
 * is the steady state: each entry is written and dropped individually, so a run
 * that dies part-way resumes exactly where it stopped and never re-applies a
 * write the user has since undone by hand.
 *
 * The reconciliation only hides (see [calendarVisibilityPlan]). Calendars hidden
 * at system level stay hidden, and on an *upgraded* install the first run that
 * sees one arms the one-time notice explaining why Calendula no longer lists
 * their events. A fresh install never had the old behaviour, so it retires that
 * notice unshown — every other device ships with something hidden.
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
            // A fresh install has no retired model behind it — nothing to
            // migrate, and nothing to explain. Settled ahead of the permission
            // gate so an update installed before the first grant can't make a
            // first run look like an upgrade afterwards.
            if (!isUpgradeInstall()) settleNoticeOnce(pending = false)
            if (!hasPermission(Manifest.permission.READ_CALENDAR)) return@withContext
            val pending = prefs.pendingDisabledCalendarIds.first()
            val noticeSettled = prefs.visibilityNoticePending.first() != null
            // The steady state, and every run after the first: nothing left to
            // drain and nothing left to decide, so don't pay for the query.
            if (pending.isEmpty() && noticeSettled) return@withContext
            val calendars = dataSource.calendars()
            // An empty read means "couldn't read", not "no calendars": the data
            // source turns a null cursor — a provider momentarily unavailable —
            // into an empty list. Both decisions below are one-way, so taking
            // that reading as the truth would drop the whole pending set without
            // ever writing VISIBLE = 0 (switching the user's calendars back on,
            // events and reminders with them) and settle the notice as "nothing
            // to explain". Leave both to the next run.
            if (calendars.isEmpty()) return@withContext
            settleNoticeOnce(hasSystemHiddenCalendars(calendars, pending))
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
     * Settle the one-time notice: [pending] arms it, false retires it unshown.
     * Answered once, by whichever run can answer it first; the answer is stored
     * either way, so the notice can't resurface later, when the same state would
     * no longer be news to the user.
     */
    private suspend fun settleNoticeOnce(pending: Boolean) {
        if (prefs.visibilityNoticePending.first() != null) return
        prefs.setVisibilityNoticePending(pending)
    }

    /**
     * Whether this install has ever run an earlier version. The notice explains
     * a change to behaviour the user has already seen, so a first install has
     * nothing to announce — and hidden calendars are the *norm* on a fresh
     * device (a second account's, "Holidays in …", a subscribed calendar), which
     * would otherwise put a changelog dialog in front of a first-run user.
     */
    private fun isUpgradeInstall(): Boolean = try {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime > info.firstInstallTime
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w(TAG, "Own package info unavailable; treating as a fresh install", e)
        false
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
