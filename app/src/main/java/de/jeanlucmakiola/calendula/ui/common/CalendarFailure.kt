package de.jeanlucmakiola.calendula.ui.common

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EditOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.FailureReason

/**
 * Full-screen failure state shared by every calendar screen (spec §7).
 * A tonal icon, one headline, one supporting line and one recovery action —
 * never a toast.
 */
@Composable
fun CalendarFailure(
    reason: FailureReason,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val manageCalendars = LocalManageCalendars.current
    val copy = failureCopy(reason)
    val onAction: () -> Unit = when (reason) {
        FailureReason.NoCalendarsConfigured -> {
            { context.startCalendarSetup() }
        }
        FailureReason.AllCalendarsHidden,
        FailureReason.NoImportTarget,
        -> manageCalendars ?: onRetry
        else -> onRetry
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FailureIcon(icon = copy.icon, isError = copy.isError)
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(copy.title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = TEXT_MAX_WIDTH),
        )
        copy.body?.let { body ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = TEXT_MAX_WIDTH),
            )
        }
        Spacer(Modifier.height(32.dp))
        FilledTonalButton(onClick = onAction) {
            Text(stringResource(copy.action))
        }
    }
}

/** The state's mark: the reason's icon in a tonal circle above the headline. */
@Composable
private fun FailureIcon(icon: ImageVector, isError: Boolean) {
    val container: Color
    val content: Color
    if (isError) {
        container = MaterialTheme.colorScheme.errorContainer
        content = MaterialTheme.colorScheme.onErrorContainer
    } else {
        container = MaterialTheme.colorScheme.secondaryContainer
        content = MaterialTheme.colorScheme.onSecondaryContainer
    }
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(36.dp),
        )
    }
}

/**
 * What a reason says and offers. Headline and supporting line are separate so
 * the explanation reads as body text instead of a headline that runs over three
 * lines; [isError] tints the mark for the two reasons that are an actual fault
 * rather than a calendar the user can switch back on.
 */
private data class FailureCopy(
    val icon: ImageVector,
    @StringRes val title: Int,
    @StringRes val body: Int?,
    @StringRes val action: Int,
    val isError: Boolean = false,
)

private fun failureCopy(reason: FailureReason): FailureCopy = when (reason) {
    FailureReason.PermissionRevoked -> FailureCopy(
        icon = Icons.Outlined.Lock,
        title = R.string.state_failure_permission,
        body = R.string.state_failure_permission_body,
        action = R.string.state_failure_permission_action,
    )
    FailureReason.NoCalendarsConfigured -> FailureCopy(
        icon = Icons.Outlined.CalendarMonth,
        title = R.string.state_failure_no_calendars,
        body = R.string.state_failure_no_calendars_body,
        action = R.string.state_failure_no_calendars_action,
    )
    FailureReason.AllCalendarsHidden -> FailureCopy(
        icon = Icons.Outlined.VisibilityOff,
        title = R.string.state_failure_all_hidden,
        body = R.string.state_failure_all_hidden_body,
        action = R.string.state_failure_all_hidden_action,
    )
    FailureReason.NoImportTarget -> FailureCopy(
        icon = Icons.Outlined.EditOff,
        title = R.string.state_failure_no_import_target,
        body = R.string.state_failure_no_import_target_body,
        action = R.string.state_failure_all_hidden_action,
    )
    FailureReason.EventNotFound -> FailureCopy(
        icon = Icons.Outlined.EventBusy,
        title = R.string.state_failure_event_not_found,
        body = R.string.state_failure_event_not_found_body,
        action = R.string.state_retry,
    )
    FailureReason.ProviderUnavailable -> FailureCopy(
        icon = Icons.Outlined.SyncProblem,
        title = R.string.state_failure_provider,
        body = R.string.state_failure_provider_body,
        action = R.string.state_retry,
        isError = true,
    )
    FailureReason.Unknown -> FailureCopy(
        icon = Icons.Outlined.ErrorOutline,
        title = R.string.state_failure_unknown,
        body = null,
        action = R.string.state_retry,
        isError = true,
    )
}

/** Keeps the headline and its supporting line at a readable measure. */
private val TEXT_MAX_WIDTH = 320.dp

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
