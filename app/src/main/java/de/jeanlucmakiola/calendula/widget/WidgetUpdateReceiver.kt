package de.jeanlucmakiola.calendula.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import de.jeanlucmakiola.calendula.widget.agenda.AgendaWidget
import de.jeanlucmakiola.calendula.widget.month.MonthWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
 * `DATE_CHANGED` is a free extra in the filter that nothing depends on — see
 * [WidgetRolloverScheduler]. The backstops are `updatePeriodMillis` in the
 * provider XML and the month widget's refresh button.
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
        // Re-arm first, so the next day boundary is covered whatever the redraw
        // does. Every handled action either dropped, consumed or invalidated it.
        WidgetRolloverScheduler.sync(appContext)
        // The host sends APPWIDGET_UPDATE after both of these anyway, so
        // redrawing here would only repeat the work in a cold process, at the
        // moment the device is most contended.
        if (intent.action in REARM_ONLY_ACTIONS) return
        val pending = goAsync()
        // Calendar data may have changed (sync / our own write) — drop the cached
        // month window so the widgets reload fresh. Month paging does NOT call
        // this, so arrow taps stay instant.
        invalidateMonthWidgetCache()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                AgendaWidget().updateAll(appContext)
                MonthWidget().updateAll(appContext)
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

        internal val HANDLED_ACTIONS = REARM_ONLY_ACTIONS + setOf(
            ACTION_ROLLOVER,
            Intent.ACTION_PROVIDER_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
