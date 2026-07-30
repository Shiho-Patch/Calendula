package de.jeanlucmakiola.calendula.ui.settings

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.WeekStartPref
import de.jeanlucmakiola.calendula.data.prefs.resolveFirstDay
import de.jeanlucmakiola.calendula.ui.common.PickerDescription
import de.jeanlucmakiola.calendula.ui.month.MonthStylePreview
import de.jeanlucmakiola.calendula.ui.month.MonthViewStyle
import de.jeanlucmakiola.floret.components.FullScreenPicker
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.SelectedCheck
import de.jeanlucmakiola.floret.components.positionOf
import de.jeanlucmakiola.floret.identity.rememberReduceMotion
import de.jeanlucmakiola.floret.locale.currentLocale

/**
 * The week-start chooser, with the grid it rearranges shown above it.
 *
 * Which column a week opens on is the most visual setting in the app, and the
 * one hardest to hold in your head from a weekday name alone. The preview is
 * the same [MonthStylePreview] the Month style picker uses, drawn in the user's
 * *own* month style, so what moves is exactly what will move on the real
 * screen. It is short here (the option list runs to eight rows), which the
 * preview supports natively by scaling.
 *
 * "Follow the system" carries the day it currently resolves to as its summary —
 * otherwise the one automatic option is the only one whose effect is invisible.
 *
 * A preview picker, so selecting applies at once and keeps the picker open;
 * back exits (see `FullScreenPicker`).
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
    val reduceMotion = rememberReduceMotion()
    val resolved = selected.resolveFirstDay(locale)
    FullScreenPicker(
        title = stringResource(R.string.settings_week_start),
        onDismiss = onDismiss,
        predictiveBack = true,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .height(PREVIEW_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                targetState = resolved,
                animationSpec = if (reduceMotion) snap() else tween(durationMillis = 250),
                label = "week-start-preview",
            ) { day ->
                Box(
                    modifier = Modifier
                        .clip(PREVIEW_SHAPE)
                        .background(MaterialTheme.colorScheme.surface)
                        .clipToBounds(),
                ) {
                    MonthStylePreview(
                        style = monthStyle,
                        weekStart = day,
                        height = PREVIEW_HEIGHT,
                    )
                }
            }
        }
        PickerDescription(stringResource(R.string.settings_week_start_hint))
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            GroupedRow(
                title = weekStartLabel(option),
                // Only the automatic option needs a second line: it is the one
                // whose effect the label doesn't name.
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
private val PREVIEW_SHAPE = RoundedCornerShape(12.dp)
