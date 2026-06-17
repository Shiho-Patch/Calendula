package de.jeanlucmakiola.calendula.widget.month

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Host-facing receiver for the month widget. Declared in the manifest with the
 * `appwidget_info_month` provider metadata; delegates rendering to [MonthWidget].
 */
class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}
