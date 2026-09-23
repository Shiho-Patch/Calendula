package de.jeanlucmakiola.calendula.ui.onboarding

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.ui.settings.openUrl
import de.jeanlucmakiola.floret.components.BenefitRow
import de.jeanlucmakiola.floret.components.OnboardingScaffold
import de.jeanlucmakiola.floret.components.OnboardingSpace

/** Where the sync branch sends someone who needs a CalDAV client (#287). */
private const val DAVX5_URL = "https://www.davx5.com/"

/**
 * Wizard step shown only when the device has no calendar at all (#287): the app
 * would otherwise finish onboarding onto an empty grid with nothing saying why.
 *
 * Three ways out, in the order most people want them. Calendula writes to the
 * system calendar provider and syncs nothing itself, so two of the three are
 * handing off to something that does.
 */
@Composable
internal fun CalendarsStep(
    onCreateLocal: (String) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    progress: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val localName = stringResource(R.string.onboarding_calendars_local_name)

    OnboardingScaffold(
        modifier = modifier,
        progress = progress,
        navigationIcon = navigationIcon,
        topSpacing = OnboardingSpace.lg,
        hero = { IconHero(Icons.Filled.CalendarMonth) },
        actions = {
            Button(
                onClick = { onCreateLocal(localName) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_calendars_local_button),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            OutlinedButton(
                onClick = { context.startCalendarAccountSetup() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(stringResource(R.string.onboarding_calendars_account_button))
            }
            TextButton(
                onClick = { openUrl(context, DAVX5_URL) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_calendars_caldav_button))
            }
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_calendars_skip_button))
            }
        },
    ) {
        Text(
            text = stringResource(R.string.onboarding_calendars_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_calendars_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(OnboardingSpace.lg))

        BenefitRow(
            icon = Icons.Filled.PhoneAndroid,
            title = stringResource(R.string.onboarding_calendars_benefit_local_title),
            body = stringResource(R.string.onboarding_calendars_benefit_local_body),
        )
        Spacer(Modifier.height(OnboardingSpace.sm))
        BenefitRow(
            icon = Icons.Filled.CloudSync,
            title = stringResource(R.string.onboarding_calendars_benefit_sync_title),
            body = stringResource(R.string.onboarding_calendars_benefit_sync_body),
        )
    }
}

/**
 * Open the system screen for putting an account's calendars on the device: the
 * account chooser filtered to calendar providers, falling back to sync settings
 * where no chooser resolves. Both are system components, so neither needs a
 * `<queries>` entry. Mirrors the same hand-off in `CalendarFailure`.
 */
private fun Context.startCalendarAccountSetup() {
    val intents = listOf(
        Intent(Settings.ACTION_ADD_ACCOUNT)
            .putExtra(Settings.EXTRA_AUTHORITIES, arrayOf(CalendarContract.AUTHORITY)),
        Intent(Settings.ACTION_SYNC_SETTINGS),
    )
    intents.firstOrNull { runCatching { startActivity(it) }.isSuccess }
}
