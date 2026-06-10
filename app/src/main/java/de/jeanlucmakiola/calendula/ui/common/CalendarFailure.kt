package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.FailureReason

/**
 * Full-screen failure state shared by every calendar screen (spec §7).
 * One explanation line + one recovery action, never a toast.
 */
@Composable
fun CalendarFailure(reason: FailureReason, onRetry: () -> Unit) {
    val titleRes = when (reason) {
        FailureReason.PermissionRevoked -> R.string.state_failure_permission
        FailureReason.NoCalendarsConfigured -> R.string.state_failure_no_calendars
        FailureReason.ProviderUnavailable -> R.string.state_failure_provider
        FailureReason.Unknown,
        FailureReason.EventNotFound -> R.string.state_failure_unknown
    }
    val actionRes = when (reason) {
        FailureReason.NoCalendarsConfigured -> R.string.state_failure_no_calendars_action
        FailureReason.PermissionRevoked -> R.string.state_failure_permission_action
        else -> R.string.state_retry
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(onClick = onRetry) {
            Text(stringResource(actionRes))
        }
    }
}
