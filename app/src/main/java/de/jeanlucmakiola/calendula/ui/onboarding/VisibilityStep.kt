package de.jeanlucmakiola.calendula.ui.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.floret.components.BenefitRow
import de.jeanlucmakiola.floret.components.OnboardingScaffold
import de.jeanlucmakiola.floret.components.OnboardingSpace

/**
 * Wizard step for the device's own per-calendar visibility (#75, #163, #287),
 * in three faces:
 *
 * - the one-time notice that Calendula now follows those switches and some of
 *   yours are off — an announcement with nothing to decide, since switching a
 *   calendar back on would un-hide it in every other calendar app too;
 * - nothing visible at all, where that same consequence is worth accepting: the
 *   grid is empty until something is switched on, so the step offers to do it;
 * - what the app did once that offer was taken.
 */
@Composable
internal fun VisibilityStep(
    state: VisibilityUi,
    onFinished: () -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
    progress: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
) {
    if (state.fix == VisibilityFix.SwitchedOn) {
        CalendarsSwitchedOn(
            onContinue = onFinished,
            modifier = modifier,
            progress = progress,
            navigationIcon = navigationIcon,
        )
        return
    }
    if (state.notice) {
        VisibilityNotice(
            onFinished = onFinished,
            modifier = modifier,
            progress = progress,
            navigationIcon = navigationIcon,
        )
        return
    }
    AllHidden(
        onShowAll = onShowAll,
        onSkip = onFinished,
        failed = state.fix == VisibilityFix.Failed,
        modifier = modifier,
        progress = progress,
        navigationIcon = navigationIcon,
    )
}

/** The one-time #75 notice: what changed, and where the switches live. */
@Composable
private fun VisibilityNotice(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    progress: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
) {
    OnboardingScaffold(
        modifier = modifier,
        progress = progress,
        navigationIcon = navigationIcon,
        topSpacing = OnboardingSpace.lg,
        hero = { IconHero(Icons.Filled.VisibilityOff) },
        actions = {
            Button(
                onClick = onFinished,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_visibility_button),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
    ) {
        Text(
            text = stringResource(R.string.calendars_visibility_notice_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.calendars_visibility_notice_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Every calendar switched off (#287): the one state where the step has something
 * to offer rather than announce. Skippable — a phone whose calendars are all off
 * may well be meant that way.
 */
@Composable
private fun AllHidden(
    onShowAll: () -> Unit,
    onSkip: () -> Unit,
    failed: Boolean,
    modifier: Modifier = Modifier,
    progress: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
) {
    OnboardingScaffold(
        modifier = modifier,
        progress = progress,
        navigationIcon = navigationIcon,
        topSpacing = OnboardingSpace.lg,
        hero = { IconHero(Icons.Filled.VisibilityOff) },
        actions = {
            Button(
                onClick = onShowAll,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_visibility_hidden_button),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_visibility_hidden_skip))
            }
        },
    ) {
        Text(
            text = stringResource(R.string.onboarding_visibility_hidden_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_visibility_hidden_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (failed) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onboarding_visibility_hidden_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(OnboardingSpace.lg))

        BenefitRow(
            icon = Icons.Filled.NotificationsOff,
            title = stringResource(R.string.onboarding_visibility_hidden_benefit_off_title),
            body = stringResource(R.string.onboarding_visibility_hidden_benefit_off_body),
        )
        Spacer(Modifier.height(OnboardingSpace.sm))
        BenefitRow(
            icon = Icons.Filled.Settings,
            title = stringResource(R.string.onboarding_visibility_hidden_benefit_settings_title),
            body = stringResource(R.string.onboarding_visibility_hidden_benefit_settings_body),
        )
    }
}

/** What the step shows once it has switched them on: that it did, and where to undo it. */
@Composable
private fun CalendarsSwitchedOn(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    progress: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
) {
    OnboardingScaffold(
        modifier = modifier,
        progress = progress,
        navigationIcon = navigationIcon,
        topSpacing = OnboardingSpace.lg,
        hero = { IconHero(Icons.Filled.Visibility) },
        actions = {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_view_continue_button),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
    ) {
        Text(
            text = stringResource(R.string.onboarding_visibility_switched_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_visibility_switched_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
