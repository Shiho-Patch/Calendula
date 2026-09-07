package de.jeanlucmakiola.calendula.ui.common

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.provider.Settings
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val manageCalendars = LocalManageCalendars.current
    val titleRes = when (reason) {
        FailureReason.PermissionRevoked -> R.string.state_failure_permission
        FailureReason.NoCalendarsConfigured -> R.string.state_failure_no_calendars
        FailureReason.AllCalendarsHidden -> R.string.state_failure_all_hidden
        FailureReason.ProviderUnavailable -> R.string.state_failure_provider
        FailureReason.Unknown,
        FailureReason.EventNotFound -> R.string.state_failure_unknown
    }
    val actionRes = when (reason) {
        FailureReason.NoCalendarsConfigured -> R.string.state_failure_no_calendars_action
        FailureReason.AllCalendarsHidden -> R.string.state_failure_all_hidden_action
        FailureReason.PermissionRevoked -> R.string.state_failure_permission_action
        else -> R.string.state_retry
    }
    val onAction: () -> Unit = when (reason) {
        FailureReason.NoCalendarsConfigured -> {
            { context.startCalendarSetup() }
        }
        FailureReason.AllCalendarsHidden -> manageCalendars ?: onRetry
        else -> onRetry
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
        FilledTonalButton(onClick = onAction) {
            Text(stringResource(actionRes))
        }
    }
}

/**
 * Opens Settings → Calendars, the only screen that can switch a calendar back
 * on. Null outside the calendar host — screens without it never raise the
 * reason that needs it, and fall back to their own retry.
 */
val LocalManageCalendars = compositionLocalOf<(() -> Unit)?> { null }

/**
 * Open the system screen for putting a calendar on the device: the account
 * chooser filtered to calendar providers, falling back to account settings
 * where no chooser resolves. Both are system components, so nothing here needs
 * a `<queries>` entry.
 */
private fun Context.startCalendarSetup() {
    val intents = listOf(
        Intent(Settings.ACTION_ADD_ACCOUNT)
            .putExtra(Settings.EXTRA_AUTHORITIES, arrayOf(CalendarContract.AUTHORITY)),
        Intent(Settings.ACTION_SYNC_SETTINGS),
    )
    intents.firstOrNull { runCatching { startActivity(it) }.isSuccess }
}
