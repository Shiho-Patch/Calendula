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
 * Keeps [CalendarPrefs.pendingDisabledCalendarIds] and the system's
 * `Calendars.VISIBLE` in step (#75): the fold-in of the retired app-local
 * visibility model, and the standing drain for switch-offs made without
 * `WRITE_CALENDAR`.
 *
 * Runs on every launch and on every calendar-permission grant; a no-op once the
 * pending set is empty and the notice is settled. Only ever hides (see
 * [calendarVisibilityPlan]); on an upgraded install the first run that sees a
 * system-hidden calendar arms the one-time explanatory notice.
 */
@Singleton
class CalendarVisibilityReconciler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataSource: CalendarDataSource,
    private val prefs: CalendarPrefs,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    suspend fun run() = withContext(io) {
        // The DataStore reads sit inside the guard too: this runs in a bare
        // application-scope coroutine, so an IOException from a damaged
        // preferences file would take the process down on every launch.
        try {
            // Settled ahead of the permission gate, so an update installed
            // before the first grant can't later look like an upgrade.
            if (!isUpgradeInstall()) settleNoticeOnce(pending = false)
            if (!hasPermission(Manifest.permission.READ_CALENDAR)) return@withContext
            val pending = prefs.pendingDisabledCalendarIds.first()
            val noticeSettled = prefs.visibilityNoticePending.first() != null
            if (pending.isEmpty() && noticeSettled) return@withContext
            val calendars = dataSource.calendars()
            // Empty means "couldn't read" (null cursor), not "no calendars".
            // Both decisions below are one-way, so leave them to the next run.
            if (calendars.isEmpty()) return@withContext
            settleNoticeOnce(hasSystemHiddenCalendars(calendars, pending))
            if (pending.isEmpty() || !hasPermission(Manifest.permission.WRITE_CALENDAR)) {
                return@withContext
            }
            val plan = calendarVisibilityPlan(calendars, pending)
            prefs.removePendingDisabledCalendarIds(plan.settled)
            // One calendar per write: the provider skips its reminder-alarm
            // reschedule for anything but a single-id update (see
            // [CalendarDataSource.setCalendarVisible]).
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
     * Answered once and stored either way, so it can't resurface later.
     */
    private suspend fun settleNoticeOnce(pending: Boolean) {
        if (prefs.visibilityNoticePending.first() != null) return
        prefs.setVisibilityNoticePending(pending)
    }

    /**
     * Whether this install has ever run an earlier version. The notice explains
     * a behaviour change, so a first install has nothing to announce.
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
