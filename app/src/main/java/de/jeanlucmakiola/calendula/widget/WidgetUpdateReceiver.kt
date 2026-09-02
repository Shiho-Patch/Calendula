package de.jeanlucmakiola.calendula.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.glance.appwidget.updateAll
import de.jeanlucmakiola.calendula.widget.agenda.AgendaWidget
import de.jeanlucmakiola.calendula.widget.month.MonthWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Redraws both home-screen widgets when their data goes stale, and keeps the
 * midnight rollover alarm armed. Triggered by:
 *  - `PROVIDER_CHANGED` from the calendar provider — fires on any data change,
 *    so it covers both the app's own writes and external sync.
 *  - [ACTION_ROLLOVER], the app's own alarm from [WidgetRolloverScheduler] —
 *    the day boundary, so "today" highlighting and the agenda's past-event
 *    dimming move on (#228).
 *  - `TIME_SET` / `TIMEZONE_CHANGED` — the day boundary moved, so redraw *and*
 *    re-arm.
 *  - `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` — both wipe pending alarms; the
 *    latter is also what arms installs upgrading into the fix.
 *
 * `DATE_CHANGED` rides along as a free extra the system may or may not send —
 * see [WidgetRolloverScheduler]. The backstops are `updatePeriodMillis` in the
 * provider XML and the month widget's refresh button.
 *
 * Every action that dropped, consumed or invalidated the alarm re-arms it, and
 * `PROVIDER_CHANGED` re-arms at most once every [DATA_REARM_INTERVAL_MILLIS]
 * ([dataRearmDue]). The alarm is inexact, so the system may drop it outright in
 * a restricted standby bucket; nothing in ordinary daily use would then notice,
 * and the widget sits on yesterday's date. A calendar change is the one signal
 * that keeps arriving — but it arrives on *every* write (each drag, each event
 * of a thousand-event import), which is what the interval is for.
 *
 * Exported for the system broadcasts; an extra redraw from another app is
 * harmless.
 */
class WidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Exported, so anything can reach it with an explicit intent. Nothing
        // here crosses a trust boundary, but narrowing to the actions we asked
        // for keeps a stray broadcast from costing two wide provider reads.
        if (intent.action !in HANDLED_ACTIONS) return
        val appContext = context.applicationContext
        val rearm = intent.action in REARM_ACTIONS ||
            (intent.action == Intent.ACTION_PROVIDER_CHANGED && dataRearmDue())
        // The host sends APPWIDGET_UPDATE after the re-arm-only ones anyway, so
        // redrawing there would only repeat the work in a cold process, at the
        // moment the device is most contended.
        val redraw = intent.action !in REARM_ONLY_ACTIONS
        val pending = goAsync()
        // Calendar data may have changed (sync / our own write) — drop the cached
        // month window so the widgets reload fresh. Month paging does NOT call
        // this, so arrow taps stay instant.
        if (redraw) invalidateMonthWidgetCache()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Re-armed first, so the next day boundary is covered whatever
                // the redraw does — and off the receiving thread, since it is a
                // handful of binder calls (`CalendulaApp` moves it for the same
                // reason).
                if (rearm) WidgetRolloverScheduler.sync(appContext)
                if (redraw) {
                    AgendaWidget().updateAll(appContext)
                    MonthWidget().updateAll(appContext)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** The app's own midnight wake-up; see [WidgetRolloverScheduler]. */
        const val ACTION_ROLLOVER = "de.jeanlucmakiola.calendula.widget.ROLLOVER"

        /** Both wipe pending alarms, and the host redraws the widgets itself after them. */
        private val REARM_ONLY_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )

        /**
         * The actions that leave no alarm standing: the two above wipe it, the
         * rollover consumed itself, and a clock, zone or day change moved the
         * boundary the pending one was set for.
         */
        internal val REARM_ACTIONS = REARM_ONLY_ACTIONS + setOf(
            ACTION_ROLLOVER,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
        )

        internal val HANDLED_ACTIONS = REARM_ACTIONS + Intent.ACTION_PROVIDER_CHANGED

        /** How rarely a calendar change may re-arm; see the class doc. */
        private const val DATA_REARM_INTERVAL_MILLIS = 15 * 60_000L

        /** Uptime of the last data-change re-arm, 0 for none in this process. */
        private val lastDataRearmMillis = AtomicLong(0L)

        /**
         * Whether a calendar change may re-arm now — the backstop for an alarm
         * the system dropped, throttled so a burst of writes costs one pass.
         */
        private fun dataRearmDue(): Boolean {
            val now = SystemClock.elapsedRealtime()
            val last = lastDataRearmMillis.get()
            val due = last == 0L || now - last >= DATA_REARM_INTERVAL_MILLIS
            return due && lastDataRearmMillis.compareAndSet(last, now)
        }
    }
}
