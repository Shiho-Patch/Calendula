package de.jeanlucmakiola.calendula.widget.agenda

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Host-facing receiver for the agenda widget. Declared in the manifest with the
 * `appwidget_info_agenda` provider metadata; delegates all rendering to
 * [AgendaWidget].
 */
class AgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaWidget()
}
