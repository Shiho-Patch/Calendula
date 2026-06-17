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
 * Redraws both home-screen widgets when their data goes stale. Triggered by:
 *  - `PROVIDER_CHANGED` from the calendar provider — fires on any data change,
 *    so it covers both the app's own writes and external sync.
 *  - `DATE_CHANGED` / `TIME_SET` / `TIMEZONE_CHANGED` — so "today" highlighting
 *    and the upcoming window roll over at midnight / on a clock change.
 *
 * Both widgets also carry an `updatePeriodMillis` backstop in their provider
 * XML, and the month widget's refresh button forces an immediate redraw.
 */
class WidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
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
}
