package de.jeanlucmakiola.calendula.widget.month

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
}
