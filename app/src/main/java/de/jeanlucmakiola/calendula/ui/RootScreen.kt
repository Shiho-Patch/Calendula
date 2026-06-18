package de.jeanlucmakiola.calendula.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import de.jeanlucmakiola.calendula.ui.permission.PermissionScreen
import de.jeanlucmakiola.calendula.ui.permission.ReminderOnboardingScreen
import de.jeanlucmakiola.calendula.ui.permission.ReminderOnboardingViewModel

@Composable
fun RootScreen(
    modifier: Modifier = Modifier,
    requestedDetailKey: LongArray? = null,
    onDetailKeyConsumed: () -> Unit = {},
    widgetNavRequest: WidgetNavRequest? = null,
    onWidgetNavConsumed: () -> Unit = {},
    requestedImportUri: android.net.Uri? = null,
    onImportConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_CALENDAR
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    if (hasPermission) {
        // Second onboarding gate (v1.4, one-time): reminder notifications.
        // Null until DataStore's first emission — render nothing for that
        // frame instead of flashing the wrong screen.
        val reminderOnboarding: ReminderOnboardingViewModel = hiltViewModel()
        val onboardingDone by reminderOnboarding.onboardingDone.collectAsStateWithLifecycle()
        when (onboardingDone) {
            true -> CalendarHost(
                modifier = modifier,
                requestedDetailKey = requestedDetailKey,
                onDetailKeyConsumed = onDetailKeyConsumed,
                widgetNavRequest = widgetNavRequest,
                onWidgetNavConsumed = onWidgetNavConsumed,
                requestedImportUri = requestedImportUri,
                onImportConsumed = onImportConsumed,
            )
            false -> ReminderOnboardingScreen(
                onFinished = reminderOnboarding::finish,
                modifier = modifier,
            )
            null -> {}
        }
    } else {
        PermissionScreen(
            onGranted = { hasPermission = true },
            modifier = modifier,
        )
    }
}
