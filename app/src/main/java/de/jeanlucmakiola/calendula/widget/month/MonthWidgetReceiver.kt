package de.jeanlucmakiola.calendula.widget.month

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import de.jeanlucmakiola.calendula.widget.WidgetRolloverScheduler

/**
 * Host-facing receiver for the month widget. Declared in the manifest with the
 * `appwidget_info_month` provider metadata; delegates rendering to [MonthWidget].
 */
class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()

    /** First month widget placed — start rolling "today" over at midnight (#228). */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRolloverScheduler.sync(context)
    }

    /**
     * Last month widget removed. [WidgetRolloverScheduler.sync] cancels only if
     * no agenda widget is left either.
     */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRolloverScheduler.sync(context)
    }

    /**
     * The `updatePeriodMillis` backstop is the one wake-up the *system* still
     * owns, so it doubles as the alarm's self-heal: anything that drops a
     * pending alarm without a broadcast (force-stop, battery restriction, an OEM
     * freeze) is repaired here rather than on the next app open. Re-arming
     * closer to midnight also narrows the inexact delivery window.
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetRolloverScheduler.sync(context)
    }
}
