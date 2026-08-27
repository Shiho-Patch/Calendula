package de.jeanlucmakiola.calendula.widget.agenda

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import de.jeanlucmakiola.calendula.widget.WidgetRolloverScheduler

/**
 * Host-facing receiver for the agenda widget. Declared in the manifest with the
 * `appwidget_info_agenda` provider metadata; delegates all rendering to
 * [AgendaWidget].
 */
class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()

    /** First agenda widget placed — start rolling "today" over at midnight (#228). */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRolloverScheduler.sync(context)
    }

    /**
     * Last agenda widget removed. [WidgetRolloverScheduler.sync] only cancels the
     * alarm if no month widget is left either, so removing one kind never stops
     * the other from rolling over.
     */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRolloverScheduler.sync(context)
    }

    /**
     * Self-heal on the system's own `updatePeriodMillis` wake-up — see
     * [de.jeanlucmakiola.calendula.widget.month.MonthWidgetReceiver.onUpdate].
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
