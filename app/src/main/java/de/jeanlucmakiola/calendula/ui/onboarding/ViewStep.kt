package de.jeanlucmakiola.calendula.ui.onboarding

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.resolveFirstDay
import de.jeanlucmakiola.calendula.ui.agenda.AgendaViewPreview
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.IMPLEMENTED_VIEWS
import de.jeanlucmakiola.calendula.ui.common.PickerDescription
import de.jeanlucmakiola.calendula.ui.common.ViewPreviewFrame
import de.jeanlucmakiola.calendula.ui.common.icon
import de.jeanlucmakiola.calendula.ui.common.labelRes
import de.jeanlucmakiola.calendula.ui.day.DayViewPreview
import de.jeanlucmakiola.calendula.ui.month.MonthStylePreview
import de.jeanlucmakiola.calendula.ui.month.MonthViewStyle
import de.jeanlucmakiola.calendula.ui.month.descriptionRes
import de.jeanlucmakiola.calendula.ui.month.labelRes
import de.jeanlucmakiola.calendula.ui.week.WeekViewPreview
import de.jeanlucmakiola.floret.components.GroupedListInset
import de.jeanlucmakiola.floret.components.OnboardingScaffold
import de.jeanlucmakiola.floret.components.OnboardingSpace
import de.jeanlucmakiola.floret.components.GroupedRow
import de.jeanlucmakiola.floret.components.SelectedCheck
import de.jeanlucmakiola.floret.components.positionOf
import de.jeanlucmakiola.floret.locale.currentLocale

/**
 * Wizard step: which view the app opens on (#163).
 *
 * Built like the Settings pickers it mirrors — a live preview over connected
 * grouped rows — so the choice is made by looking rather than by reading four
 * names and guessing, and so the same choice looks the same wherever it is
 * made. Selecting applies immediately and leaves the step open; the preview
 * above is the confirmation.
 */
@Composable
internal fun ViewStep(
    choice: ViewChoice,
    onSelect: (CalendarView) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    progress: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
) {
    val weekStart = choice.weekStart.resolveFirstDay(currentLocale())

    StepPickerScaffold(
        title = stringResource(R.string.onboarding_view_title),
        onFinished = onFinished,
        modifier = modifier,
        progress = progress,
        navigationIcon = navigationIcon,
    ) {
        StepPreview(selected = choice.defaultView) { view ->
            when (view) {
                CalendarView.Month -> MonthStylePreview(
                    style = choice.monthViewStyle,
                    weekStart = weekStart,
                    height = PREVIEW_HEIGHT,
                )
                CalendarView.Week -> WeekViewPreview(weekStart = weekStart, height = PREVIEW_HEIGHT)
                CalendarView.Day -> DayViewPreview(height = PREVIEW_HEIGHT)
                CalendarView.Agenda -> AgendaViewPreview(height = PREVIEW_HEIGHT)
            }
        }
        IMPLEMENTED_VIEWS.forEachIndexed { index, view ->
            val isSelected = view == choice.defaultView
            GroupedRow(
                title = stringResource(view.labelRes),
                position = positionOf(index, IMPLEMENTED_VIEWS.size),
                selected = isSelected,
                leading = {
                    Icon(
                        imageVector = view.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailing = if (isSelected) {
                    { SelectedCheck() }
                } else {
                    null
                },
                onClick = { onSelect(view) },
            )
        }
    }
}

/**
 * Wizard step: how the Month view lays itself out (#163). The same shape as
 * [ViewStep] and as the Settings chooser this duplicates, down to the selected
 * style's own blurb sitting under its preview rather than on every row.
 */
@Composable
internal fun MonthStyleStep(
    choice: ViewChoice,
    onSelect: (MonthViewStyle) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    progress: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
) {
    val weekStart = choice.weekStart.resolveFirstDay(currentLocale())
    val options = MonthViewStyle.entries

    StepPickerScaffold(
        title = stringResource(R.string.onboarding_month_style_title),
        onFinished = onFinished,
        modifier = modifier,
        progress = progress,
        navigationIcon = navigationIcon,
    ) {
        StepPreview(selected = choice.monthViewStyle) { style ->
            MonthStylePreview(style = style, weekStart = weekStart, height = PREVIEW_HEIGHT)
        }
        PickerDescription(stringResource(choice.monthViewStyle.descriptionRes))
        options.forEachIndexed { index, style ->
            val isSelected = style == choice.monthViewStyle
            GroupedRow(
                title = stringResource(style.labelRes),
                position = positionOf(index, options.size),
                selected = isSelected,
                trailing = if (isSelected) {
                    { SelectedCheck() }
                } else {
                    null
                },
                onClick = { onSelect(style) },
            )
        }
    }
}

/**
 * The shell both picker steps share: the wizard chrome around a title and a
 * full-bleed column, since grouped rows carry their own inset.
 */
@Composable
private fun StepPickerScaffold(
    title: String,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    progress: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    OnboardingScaffold(
        modifier = modifier,
        progress = progress,
        navigationIcon = navigationIcon,
        topSpacing = OnboardingSpace.xs,
        contentPadding = 0.dp,
        // The preview is worth more than a screenful of certainty: it gets the
        // room it needs and Continue scrolls in under the options with it.
        scrollingActions = true,
        hero = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GroupedListInset),
            )
        },
        actions = {
            Button(
                onClick = onFinished,
                // The step is full-bleed, so the button takes the grouped rows'
                // own inset and ends up as wide as the options above it.
                modifier = Modifier
                    .padding(horizontal = GroupedListInset)
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_view_continue_button),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        body = body,
    )
}

/**
 * The live preview, framed like the picker's. The gap under it is wider than the
 * one above: the rows below are the things to tap, and they need to look like a
 * group of their own rather than the preview's caption.
 */
@Composable
private fun <T> StepPreview(selected: T, content: @Composable (T) -> Unit) {
    ViewPreviewFrame(
        selected = selected,
        label = "step-preview",
        modifier = Modifier.padding(top = OnboardingSpace.xs, bottom = OnboardingSpace.md),
        content = content,
    )
}

/**
 * The same size the Settings chooser gives it. The step no longer tries to fit a
 * screen: showing the view properly is what the step is for, and a picture too
 * small to read would cost more than the scroll does.
 */
private val PREVIEW_HEIGHT: Dp = 280.dp
