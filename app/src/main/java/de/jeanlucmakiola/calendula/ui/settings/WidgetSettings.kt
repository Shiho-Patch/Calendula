package de.jeanlucmakiola.calendula.ui.settings

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.resolveFirstDay
import de.jeanlucmakiola.calendula.qs.NewEventTileService
import de.jeanlucmakiola.calendula.ui.common.AgendaRangePicker
import de.jeanlucmakiola.calendula.ui.common.PickerDescription
import de.jeanlucmakiola.calendula.ui.common.agendaRangeLabel
import de.jeanlucmakiola.calendula.widget.WidgetSize
import de.jeanlucmakiola.calendula.widget.agenda.metricsFor
import de.jeanlucmakiola.floret.components.CollapsingScaffold
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.OptionPicker
import de.jeanlucmakiola.floret.components.Position
import de.jeanlucmakiola.floret.locale.currentLocale
import kotlin.math.roundToInt

/**
 * Widgets & tiles (#69): the home-screen widgets' settings and the Quick
 * Settings tile shortcut. The agenda widget keeps its own range and text size,
 * separate from the Agenda screen's range in [ViewsScreen].
 */
@Composable
internal fun WidgetsScreen(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    visible: Boolean = true,
) {
    var showAgendaWidgetRange by remember { mutableStateOf(false) }
    var showWidgetSize by remember { mutableStateOf(false) }

    CollapsingScaffold(
        title = stringResource(R.string.settings_section_widgets),
        onBack = onBack,
        predictiveBack = true,
        visible = visible,
    ) {
        SettingsHint(stringResource(R.string.settings_widgets_hint))
        Spacer(Modifier.height(8.dp))

        GroupedRow(
            title = stringResource(R.string.settings_agenda_widget_range),
            summary = agendaRangeLabel(state.agendaWidgetRange),
            position = Position.Top,
            onClick = { showAgendaWidgetRange = true },
        )
        GroupedRow(
            title = stringResource(R.string.settings_widget_size),
            summary = widgetSizeLabel(state.widgetSize),
            position = Position.Bottom,
            onClick = { showWidgetSize = true },
        )

        // The add-tile prompt is API 33+; below that the tile is still
        // addable from the QS editor, so the row just doesn't appear.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Spacer(Modifier.height(24.dp))
            QuickSettingsTileRow()
        }
    }

    if (showAgendaWidgetRange) {
        AgendaRangePicker(
            title = stringResource(R.string.settings_agenda_widget_range),
            description = stringResource(R.string.settings_agenda_widget_range_hint),
            selected = state.agendaWidgetRange,
            weekStart = state.weekStart.resolveFirstDay(currentLocale()),
            onSelect = viewModel::setAgendaWidgetRange,
            onDismiss = { showAgendaWidgetRange = false },
        )
    }
    if (showWidgetSize) {
        OptionPicker(
            title = stringResource(R.string.settings_widget_size),
            header = { PickerDescription(stringResource(R.string.settings_widget_size_hint)) },
            predictiveBack = true,
            options = WidgetSize.entries,
            selected = state.widgetSize,
            label = { widgetSizeLabel(it) },
            // No live preview: the widget is Glance/RemoteViews, so a Compose
            // mock-up would be a second implementation free to drift.
            summary = { widgetSizeSummary(it) },
            onSelect = viewModel::setWidgetSize,
            onDismiss = { showWidgetSize = false },
        )
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun QuickSettingsTileRow() {
    val context = LocalContext.current
    GroupedRow(
        title = stringResource(R.string.settings_qs_tile),
        summary = stringResource(R.string.settings_qs_tile_hint),
        position = Position.Alone,
        onClick = { requestAddQsTile(context) },
    )
}

/**
 * Ask the system to add the "New event" Quick Settings tile (API 33+). The OS
 * shows its own confirmation dialog and handles the already-added case, so no
 * result handling is needed here.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestAddQsTile(context: Context) {
    val statusBar = context.getSystemService(StatusBarManager::class.java) ?: return
    statusBar.requestAddTileService(
        ComponentName(context, NewEventTileService::class.java),
        context.getString(R.string.qs_tile_new_event_label),
        Icon.createWithResource(context, R.drawable.ic_qs_new_event),
        context.mainExecutor,
    ) { /* result code unused — the system surfaces its own feedback */ }
}

/**
 * What a size step does, as a percentage of the smallest one's event text.
 * Derived from the widget's own [metricsFor] table rather than hardcoded.
 */
@Composable
private fun widgetSizeSummary(size: WidgetSize): String {
    val base = metricsFor(WidgetSize.SMALL).eventTitle.value
    val percent = (metricsFor(size).eventTitle.value / base * 100f).roundToInt()
    return stringResource(R.string.settings_widget_size_summary, percent)
}

@Composable
private fun widgetSizeLabel(size: WidgetSize): String = stringResource(
    when (size) {
        WidgetSize.SMALL -> R.string.settings_widget_size_small
        WidgetSize.MEDIUM -> R.string.settings_widget_size_medium
        WidgetSize.LARGE -> R.string.settings_widget_size_large
        WidgetSize.EXTRA_LARGE -> R.string.settings_widget_size_extra_large
    },
)
