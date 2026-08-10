package de.jeanlucmakiola.calendula.ui.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.floret.components.OnboardingScaffold
import de.jeanlucmakiola.floret.components.OnboardingSpace

/**
 * The wizard's closing screen (#163): the flow ends on a full screen saying so
 * rather than dropping into the calendar mid-gesture, and the brand mark wears
 * the check that the last step earned.
 */
@Composable
internal fun DoneStep(
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
        hero = { BrandHero(HeroBadge.Done) },
        actions = {
            Button(
                onClick = onFinished,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_done_button),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
    ) {
        Text(
            text = stringResource(R.string.onboarding_done_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_done_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
