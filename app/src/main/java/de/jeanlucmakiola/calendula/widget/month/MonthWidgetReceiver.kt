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
     * Last month widget removed. [WidgetRolloverScheduler.sync] only cancels the
     * alarm if no agenda widget is left either, so removing one kind never stops
     * the other from rolling over.
     */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRolloverScheduler.sync(context)
    }

    /**
     * The `updatePeriodMillis` backstop is the one wake-up the *system* still
     * owns, so it doubles as the rollover alarm's self-heal: anything that drops
     * a pending alarm without a broadcast — a force-stop, a battery-restricted
     * transition, an OEM freeze — is repaired here rather than waiting for the
     * app to be opened. Re-arming closer to midnight also narrows the inexact
     * alarm's delivery window, which scales with how far out it was set.
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
