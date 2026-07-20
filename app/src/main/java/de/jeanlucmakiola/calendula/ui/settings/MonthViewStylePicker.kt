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
import de.jeanlucmakiola.calendula.ui.common.PickerDescription
import de.jeanlucmakiola.calendula.ui.month.MonthStylePreview
import de.jeanlucmakiola.calendula.ui.month.MonthViewStyle
import de.jeanlucmakiola.calendula.ui.month.descriptionRes
import de.jeanlucmakiola.calendula.ui.month.labelRes
import de.jeanlucmakiola.floret.components.FullScreenPicker
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.SelectedCheck
import de.jeanlucmakiola.floret.components.positionOf
import de.jeanlucmakiola.floret.identity.rememberReduceMotion
import kotlinx.datetime.DayOfWeek

/**
 * The Month view style chooser (#38, #53).
 *
 * A live, scaled-down Month view sits above the options and changes as you pick
 * one, so the choice is made by looking rather than by reading "Continuous" and
 * guessing. Selecting therefore applies immediately and leaves the picker open —
 * closing on tap would hide the very thing the screen is for. Back exits, the
 * same as the App name picker, which stays open for the same reason.
 *
 * Below the preview it is the family's standard picker shape: connected grouped
 * rows with a tonal highlight and [SelectedCheck] on the current one.
 */
@Composable
internal fun MonthViewStylePicker(
    selected: MonthViewStyle,
    weekStart: DayOfWeek,
    onSelect: (MonthViewStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = MonthViewStyle.entries
    val reduceMotion = rememberReduceMotion()
    FullScreenPicker(
        title = stringResource(R.string.settings_month_view_style),
        onDismiss = onDismiss,
        predictiveBack = true,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(PREVIEW_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                targetState = selected,
                animationSpec = if (reduceMotion) snap() else tween(durationMillis = 250),
                label = "month-style-preview",
            ) { shown ->
                // The preview renders the real grid at phone size and scales it
                // down, so its own corners are square — the frame rounds it.
                Box(
                    modifier = Modifier
                        .clip(PREVIEW_SHAPE)
                        .background(MaterialTheme.colorScheme.surface)
                        .clipToBounds(),
                ) {
                    MonthStylePreview(
                        style = shown,
                        weekStart = weekStart,
                        height = PREVIEW_HEIGHT,
                    )
                }
            }
        }
        PickerDescription(stringResource(R.string.settings_month_view_style_summary))
        options.forEachIndexed { index, style ->
            val isSelected = style == selected
            GroupedRow(
                title = stringResource(style.labelRes),
                summary = stringResource(style.descriptionRes),
                position = positionOf(index, options.size),
                selected = isSelected,
                trailing = if (isSelected) {
                    { SelectedCheck() }
                } else {
                    null
                },
                // Applies straight away; the preview above is the confirmation,
                // so there is nothing to dismiss for.
                onClick = { onSelect(style) },
            )
        }
    }
}

private val PREVIEW_HEIGHT = 240.dp
private val PREVIEW_SHAPE = RoundedCornerShape(12.dp)
