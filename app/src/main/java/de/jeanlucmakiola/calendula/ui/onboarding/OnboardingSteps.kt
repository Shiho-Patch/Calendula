package de.jeanlucmakiola.calendula.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.floret.components.OnboardingProgress

/**
 * Renders the wizard's current [step] (#163). Each step commits its own answer
 * through [viewModel], which then hands the host the next one.
 */
@Composable
fun OnboardingSteps(
    step: OnboardingStep,
    plan: OnboardingPlan,
    viewModel: OnboardingViewModel,
    onPermissionGranted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Collected for every step, not just the two that draw a preview: starting
    // it when the picker composes would leave that step blank as it slides in.
    val choice by viewModel.viewChoice.collectAsStateWithLifecycle()

    // Coerced because the outgoing half of a transition may be a step the live
    // plan has since dropped — the backup step goes once the calendars say it
    // does not apply — and a "Step 0 of 3" flash is worse than a stale number.
    val index = (plan.steps.indexOf(step) + 1).coerceAtLeast(1)
    val progress: (@Composable () -> Unit)? = if (!plan.showsProgress) {
        null
    } else {
        {
            OnboardingProgress(
                step = index,
                total = plan.total,
                label = stringResource(R.string.onboarding_step_counter, index, plan.total),
            )
        }
    }

    // The system back gesture does the same thing as the button, and stays off
    // on the first step so back still leaves the app.
    BackHandler(enabled = plan.canGoBack) { viewModel.goBack() }
    val navigationIcon: (@Composable () -> Unit)? = if (!plan.canGoBack) {
        null
    } else {
        {
            IconButton(onClick = viewModel::goBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.onboarding_back),
                )
            }
        }
    }

    when (step) {
        OnboardingStep.Permission -> PermissionScreen(
            onGranted = onPermissionGranted,
            modifier = modifier,
            progress = progress,
        )
        OnboardingStep.Reminders -> ReminderStep(
            onFinished = viewModel::finishReminders,
            modifier = modifier,
            progress = progress,
            navigationIcon = navigationIcon,
        )
        OnboardingStep.Backup -> BackupStep(
            onEnable = viewModel::enableAutoBackup,
            onSkip = viewModel::skipBackup,
            modifier = modifier,
            progress = progress,
            navigationIcon = navigationIcon,
        )
        // The choice is null only until DataStore's first emission; rendering
        // nothing for that frame beats a preview built on the wrong defaults.
        OnboardingStep.View -> choice?.let {
            ViewStep(
                choice = it,
                onSelect = viewModel::setDefaultView,
                onFinished = viewModel::finishView,
                modifier = modifier,
                progress = progress,
                navigationIcon = navigationIcon,
            )
        }
        OnboardingStep.MonthStyle -> choice?.let {
            MonthStyleStep(
                choice = it,
                onSelect = viewModel::setMonthViewStyle,
                onFinished = viewModel::finishMonthStyle,
                modifier = modifier,
                progress = progress,
                navigationIcon = navigationIcon,
            )
        }
        OnboardingStep.Visibility -> VisibilityStep(
            onFinished = viewModel::finishVisibility,
            modifier = modifier,
            progress = progress,
            navigationIcon = navigationIcon,
        )
        OnboardingStep.Done -> DoneStep(
            onFinished = viewModel::finishOnboarding,
            modifier = modifier,
            progress = progress,
            navigationIcon = navigationIcon,
        )
    }
}
