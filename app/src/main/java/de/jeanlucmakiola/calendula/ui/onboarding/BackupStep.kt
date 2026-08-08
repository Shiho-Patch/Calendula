package de.jeanlucmakiola.calendula.ui.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Schedule
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
 * Wizard step shown only when everything you write to is a device-only calendar
 * (#163): the events exist nowhere but this phone, so losing it loses them.
 *
 * The primary action picks a folder and switches automatic backup on in one go
 * — the step changes something rather than pointing at Settings. Everything it
 * sets is a normal Backup setting, so it stays adjustable afterwards.
 */
@Composable
internal fun BackupStep(
    onEnable: (Uri) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    progress: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
) {
    val pickFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        // Cancelling the picker leaves the step open: nothing was decided, and
        // "Not now" is right there for the user who meant to decline.
        uri?.let(onEnable)
    }

    OnboardingScaffold(
        modifier = modifier,
        progress = progress,
        navigationIcon = navigationIcon,
        topSpacing = OnboardingSpace.lg,
        hero = { IconHero(Icons.Filled.CloudOff) },
        actions = {
            Button(
                onClick = { runCatching { pickFolder.launch(null) } },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_backup_enable_button),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_backup_skip_button))
            }
        },
    ) {
        Text(
            text = stringResource(R.string.onboarding_backup_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_backup_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(OnboardingSpace.lg))

        BenefitRow(
            icon = Icons.Filled.FolderOpen,
            title = stringResource(R.string.onboarding_backup_benefit_folder_title),
            body = stringResource(R.string.onboarding_backup_benefit_folder_body),
        )
        Spacer(Modifier.height(OnboardingSpace.sm))
        BenefitRow(
            icon = Icons.Filled.Schedule,
            title = stringResource(R.string.onboarding_backup_benefit_daily_title),
            body = stringResource(R.string.onboarding_backup_benefit_daily_body),
        )
    }
}
