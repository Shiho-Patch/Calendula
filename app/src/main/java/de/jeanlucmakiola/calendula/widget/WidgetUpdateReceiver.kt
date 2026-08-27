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
 *  - `TIME_SET` / `TIMEZONE_CHANGED` — a clock or zone change moves the day
 *    boundary relative to the armed alarm, so both redraw *and* re-arm.
 *  - `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` — both wipe pending alarms. The
 *    package-replaced one is also what arms existing installs that upgrade into
 *    the fix without re-adding their widget.
 *
 * `DATE_CHANGED` is still in the manifest filter as a free extra, but nothing
 * depends on it: it is not an exempted implicit broadcast, so a manifest-declared
 * receiver has not actually been given it since Android 8. The widgets also carry
 * an `updatePeriodMillis` backstop in their provider XML, and the month widget's
 * refresh button forces an immediate redraw.
 *
 * Exported for the system broadcasts; an extra redraw triggered by another app
 * is harmless.
 */
class WidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // The receiver has to stay exported for the system broadcasts, so an
        // explicit intent can reach it with anything in it. Nothing here reads
        // the intent's data and nothing crosses a trust boundary, but narrowing
        // to the actions we actually asked for keeps a stray broadcast from
        // costing two wide provider reads.
        if (intent.action !in HANDLED_ACTIONS) return
        val appContext = context.applicationContext
        // Re-arm first: whatever happens to the redraw, the next day boundary is
        // covered. Boot and package-replace dropped the alarm outright; a
        // rollover just consumed it; a clock change invalidated it.
        WidgetRolloverScheduler.sync(appContext)
        // Boot and package-replace only cost us the alarm. The host sends
        // APPWIDGET_UPDATE after both anyway, so redrawing here would just repeat
        // two wide provider reads and two RemoteViews serialisations in a cold
        // process, at the moment the device is most contended.
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
