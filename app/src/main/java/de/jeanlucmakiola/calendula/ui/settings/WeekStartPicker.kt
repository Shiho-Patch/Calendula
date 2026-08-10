package de.jeanlucmakiola.calendula.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.WeekStartPref
import de.jeanlucmakiola.calendula.data.prefs.resolveFirstDay
import de.jeanlucmakiola.calendula.ui.common.PickerDescription
import de.jeanlucmakiola.calendula.ui.common.ViewPreviewFrame
import de.jeanlucmakiola.calendula.ui.month.MonthStylePreview
import de.jeanlucmakiola.calendula.ui.month.MonthViewStyle
import de.jeanlucmakiola.floret.components.FullScreenPicker
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.SelectedCheck
import de.jeanlucmakiola.floret.components.positionOf
import de.jeanlucmakiola.floret.locale.currentLocale

/**
 * The week-start chooser, with the grid it rearranges above it — the same
 * [MonthStylePreview] the Month style picker uses, drawn in the user's own month
 * style. "Follow the system" carries the day it resolves to as its summary.
 *
 * A preview picker, so selecting applies at once and keeps the picker open.
 */
@Composable
internal fun WeekStartPicker(
    selected: WeekStartPref,
    monthStyle: MonthViewStyle,
    options: List<WeekStartPref>,
    onSelect: (WeekStartPref) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = currentLocale()
    val resolved = selected.resolveFirstDay(locale)
    FullScreenPicker(
        title = stringResource(R.string.settings_week_start),
        onDismiss = onDismiss,
        predictiveBack = true,
    ) {
        ViewPreviewFrame(
            selected = resolved,
            label = "week-start-preview",
            modifier = Modifier.padding(vertical = 8.dp),
        ) { day ->
            MonthStylePreview(
                style = monthStyle,
                weekStart = day,
                height = PREVIEW_HEIGHT,
            )
        }
        PickerDescription(stringResource(R.string.settings_week_start_hint))
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            GroupedRow(
                title = weekStartLabel(option),
                summary = if (option == WeekStartPref.Auto) {
                    stringResource(
                        R.string.settings_week_start_auto_summary,
                        weekStartLabel(WeekStartPref.Day(option.resolveFirstDay(locale))),
                    )
                } else {
                    null
                },
                position = positionOf(index, options.size),
                selected = isSelected,
                trailing = if (isSelected) {
                    { SelectedCheck() }
                } else {
                    null
                },
                onClick = { onSelect(option) },
            )
        }
    }
}

/** Short enough to leave the first options on screen under it. */
private val PREVIEW_HEIGHT = 200.dp
