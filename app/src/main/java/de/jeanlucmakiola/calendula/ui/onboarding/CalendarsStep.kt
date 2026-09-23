package de.jeanlucmakiola.calendula.ui.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.data.calendar.CalendarColorPalette
import de.jeanlucmakiola.calendula.ui.calendars.CalendarEditor
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
 * Two ways out. Calendula writes to the system calendar provider and syncs
 * nothing itself, so anything beyond a device-only calendar means pointing at
 * whatever does the syncing.
 *
 * [creation] switches the step to the outcome of its own create: confirming
 * the calendar — the one place to say that more of them live in Settings — or
 * reporting that the provider refused it.
 */
@Composable
internal fun CalendarsStep(
    onCreateLocal: (name: String, color: Int, description: String?) -> Unit,
    onSkip: () -> Unit,
    creation: CalendarCreation?,
    onFinished: () -> Unit,
    onClearCreation: () -> Unit,
    modifier: Modifier = Modifier,
    progress: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    // The same editor Settings opens, rather than a calendar conjured out of
    // nowhere: "create a calendar" should let you name and colour it, and the
    // step is the first place anyone meets one.
    var creating by rememberSaveable { mutableStateOf(false) }
    // The insert is a suspend write: the editor stays up until it lands, so the
    // step can't flash its "create" button back for a second press.
    var submitted by rememberSaveable(creating) { mutableStateOf(false) }
    LaunchedEffect(creation) { if (creation is CalendarCreation.Failed) creating = false }

    if (creation is CalendarCreation.Created) {
        CalendarCreated(
            name = creation.name,
            onContinue = onFinished,
            modifier = modifier,
            progress = progress,
            navigationIcon = navigationIcon,
        )
        return
    }

    if (creating) {
        CalendarEditor(
            sessionKey = 0,
            isNew = true,
            // Empty, not a suggestion: a name already in the field reads as one
            // the app has chosen, and the first thing to do is clear it.
            initialName = "",
            initialColor = CalendarColorPalette.all.first(),
            initialDescription = "",
            onSave = { name, color, description ->
                if (!submitted) {
                    submitted = true
                    onCreateLocal(name, color, description)
                }
            },
            onDelete = {},
            onClose = { creating = false },
        )
        return
    }

    OnboardingScaffold(
        modifier = modifier,
        progress = progress,
        navigationIcon = navigationIcon,
        topSpacing = OnboardingSpace.lg,
        hero = { IconHero(Icons.Filled.CalendarMonth) },
        actions = {
            Button(
                onClick = {
                    onClearCreation()
                    creating = true
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_calendars_local_button),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            OutlinedButton(
                onClick = { openUrl(context, DAVX5_URL) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
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

        if (creation is CalendarCreation.Failed) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onboarding_calendars_create_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

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

/** What the step shows once the calendar exists: that it does, and where the next one comes from. */
@Composable
private fun CalendarCreated(
    name: String,
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
        hero = { IconHero(Icons.Filled.CheckCircle) },
        actions = {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_calendars_created_continue),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
    ) {
        Text(
            text = stringResource(R.string.onboarding_calendars_created_title, name),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_calendars_created_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(OnboardingSpace.lg))

        BenefitRow(
            icon = Icons.Filled.Settings,
            title = stringResource(R.string.onboarding_calendars_created_more_title),
            body = stringResource(R.string.onboarding_calendars_created_more_body),
        )
    }
}
