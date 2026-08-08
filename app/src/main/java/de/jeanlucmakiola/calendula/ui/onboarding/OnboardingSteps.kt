package de.jeanlucmakiola.calendula.ui.onboarding

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
    // Positioned from the step being drawn, not from the plan's own current
    // step: mid-crossfade the outgoing screen would otherwise jump to the
    // incoming one's number.
    val index = plan.steps.indexOf(step) + 1
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
        )
        OnboardingStep.Backup -> BackupStep(
            onEnable = viewModel::enableAutoBackup,
            onSkip = viewModel::skipBackup,
            modifier = modifier,
            progress = progress,
        )
        OnboardingStep.View -> {
            // Null only until DataStore's first emission; rendering nothing for
            // that frame beats a preview built on the wrong defaults.
            val choice by viewModel.viewChoice.collectAsStateWithLifecycle()
            choice?.let {
                ViewStep(
                    choice = it,
                    onSelectView = viewModel::setDefaultView,
                    onSelectMonthStyle = viewModel::setMonthViewStyle,
                    onFinished = viewModel::finishView,
                    modifier = modifier,
                    progress = progress,
                )
            }
        }
    }
}
