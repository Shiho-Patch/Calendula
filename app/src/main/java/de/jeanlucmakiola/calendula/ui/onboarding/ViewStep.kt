package de.jeanlucmakiola.calendula.ui.onboarding

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.prefs.resolveFirstDay
import de.jeanlucmakiola.calendula.ui.agenda.AgendaViewPreview
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.IMPLEMENTED_VIEWS
import de.jeanlucmakiola.calendula.ui.common.labelRes
import de.jeanlucmakiola.calendula.ui.day.DayViewPreview
import de.jeanlucmakiola.calendula.ui.month.MonthStylePreview
import de.jeanlucmakiola.calendula.ui.month.MonthViewStyle
import de.jeanlucmakiola.calendula.ui.month.descriptionRes
import de.jeanlucmakiola.calendula.ui.month.labelRes
import de.jeanlucmakiola.calendula.ui.week.WeekViewPreview
import de.jeanlucmakiola.floret.components.OnboardingScaffold
import de.jeanlucmakiola.floret.components.OnboardingSpace
import de.jeanlucmakiola.floret.identity.rememberReduceMotion
import de.jeanlucmakiola.floret.locale.currentLocale

/**
 * Last wizard step (#163): which view the app opens on, chosen by looking at it
 * rather than by reading four names and guessing.
 *
 * One preview canvas serves both choices — when Month is the pick, a second row
 * of chips for the month style appears under it and the same canvas re-renders.
 * Showing the month style on its own second preview would just be the picture
 * above it twice.
 *
 * Selections apply as they are made, so there is nothing to confirm: the single
 * action closes the step, and pressing it without touching anything is what
 * skipping means — the current defaults are pre-selected.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ViewStep(
    choice: ViewChoice,
    onSelectView: (CalendarView) -> Unit,
    onSelectMonthStyle: (MonthViewStyle) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    progress: (@Composable () -> Unit)? = null,
) {
    val reduceMotion = rememberReduceMotion()
    val weekStart = choice.weekStart.resolveFirstDay(currentLocale())

    OnboardingScaffold(
        modifier = modifier,
        progress = progress,
        topSpacing = OnboardingSpace.sm,
        hero = {
            Text(
                text = stringResource(R.string.onboarding_view_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
        },
        actions = {
            Button(
                onClick = onFinished,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_view_continue_button),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(PREVIEW_HEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                targetState = choice.defaultView to choice.monthViewStyle,
                animationSpec = if (reduceMotion) snap() else tween(durationMillis = 250),
                label = "view-preview",
            ) { (view, style) ->
                // Each preview renders the real view at phone size and scales it
                // down, so its own corners are square — the frame rounds it.
                Box(
                    modifier = Modifier
                        .clip(PREVIEW_SHAPE)
                        .background(MaterialTheme.colorScheme.surface)
                        .clipToBounds(),
                ) {
                    when (view) {
                        CalendarView.Month -> MonthStylePreview(
                            style = style,
                            weekStart = weekStart,
                            height = PREVIEW_HEIGHT,
                        )
                        CalendarView.Week -> WeekViewPreview(
                            weekStart = weekStart,
                            height = PREVIEW_HEIGHT,
                        )
                        CalendarView.Day -> DayViewPreview(height = PREVIEW_HEIGHT)
                        CalendarView.Agenda -> AgendaViewPreview(height = PREVIEW_HEIGHT)
                    }
                }
            }
        }

        Spacer(Modifier.height(OnboardingSpace.sm))
        ChipRow(
            options = IMPLEMENTED_VIEWS,
            selected = choice.defaultView,
            label = { stringResource(it.labelRes) },
            onSelect = onSelectView,
        )

        // The month style only means anything once Month is the view being
        // opened, so it stays out of the way until then.
        if (choice.defaultView == CalendarView.Month) {
            Spacer(Modifier.height(OnboardingSpace.xs))
            ChipRow(
                options = MonthViewStyle.entries,
                selected = choice.monthViewStyle,
                label = { stringResource(it.labelRes) },
                onSelect = onSelectMonthStyle,
            )
            Spacer(Modifier.height(OnboardingSpace.xs))
            Text(
                text = stringResource(choice.monthViewStyle.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** One row of single-choice chips, wrapping onto a second line where it must. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
            )
        }
    }
}

private val PREVIEW_HEIGHT = 260.dp
private val PREVIEW_SHAPE = RoundedCornerShape(12.dp)
