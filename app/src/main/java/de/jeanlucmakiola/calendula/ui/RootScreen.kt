package de.jeanlucmakiola.calendula.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.Crossfade
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import de.jeanlucmakiola.calendula.data.contacts.SpecialDatesScheduler
import de.jeanlucmakiola.calendula.data.contacts.hasContactsPermission
import de.jeanlucmakiola.calendula.ui.calendars.CalendarVisibilityNoticeDialog
import de.jeanlucmakiola.calendula.ui.calendars.CalendarVisibilityNoticeViewModel
import de.jeanlucmakiola.calendula.ui.permission.PermissionScreen
import de.jeanlucmakiola.calendula.ui.permission.ReminderOnboardingScreen
import de.jeanlucmakiola.calendula.ui.permission.ReminderOnboardingViewModel

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RootScreen(
    modifier: Modifier = Modifier,
    requestedDetailKey: LongArray? = null,
    onDetailKeyConsumed: () -> Unit = {},
    widgetNavRequest: WidgetNavRequest? = null,
    onWidgetNavConsumed: () -> Unit = {},
    requestedImportUri: android.net.Uri? = null,
    onImportConsumed: () -> Unit = {},
    requestedInsertForm: de.jeanlucmakiola.calendula.domain.EventForm? = null,
    onInsertConsumed: () -> Unit = {},
    requestedEditKey: LongArray? = null,
    onEditKeyConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED
        )
    }

    // A launch scan already covers the app coming up with the permission, so
    // only a grant made during this session owes a re-scan.
    val grantedAtLaunch = remember { hasPermission }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_CALENDAR
                ) == PackageManager.PERMISSION_GRANTED
                // Refresh the contact special-dates mirror on foreground (the
                // worker is debounced and no-ops when the feature is off). Gated
                // on the permission so users without the opt-in never enqueue it.
                if (context.hasContactsPermission()) {
                    SpecialDatesScheduler.runNow(context, foreground = true)
                }
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    // Cross-fade the one-time onboarding gates so granting permission / finishing
    // onboarding eases into the next screen instead of snapping. A fade carries no
    // spatial motion, so it stays appropriate under "remove animations" too.
    val gateSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Crossfade(targetState = hasPermission, animationSpec = gateSpec, label = "permissionGate") { granted ->
        if (granted) {
            // Second onboarding gate (v1.4, one-time): reminder notifications.
            // Null until DataStore's first emission — render nothing for that
            // frame instead of flashing the wrong screen.
            val reminderOnboarding: ReminderOnboardingViewModel = hiltViewModel()
            val onboardingDone by reminderOnboarding.onboardingDone.collectAsStateWithLifecycle()
            // One-time explainer for the switch to the device's own calendar
            // visibility (#75), armed by the reconciler.
            val visibilityNotice: CalendarVisibilityNoticeViewModel = hiltViewModel()
            val noticePending by visibilityNotice.pending.collectAsStateWithLifecycle()
            // Runs on entry however the permission was granted, including via
            // Android's app-settings screen (caught by the ON_RESUME above).
            LaunchedEffect(Unit) {
                visibilityNotice.reconcile()
                if (!grantedAtLaunch) reminderOnboarding.rearmAfterGrant()
            }
            if (onboardingDone == true && noticePending) {
                CalendarVisibilityNoticeDialog(onDismiss = visibilityNotice::dismiss)
            }
            Crossfade(targetState = onboardingDone, animationSpec = gateSpec, label = "onboardingGate") { done ->
                when (done) {
                    true -> CalendarHost(
                        modifier = modifier,
                        requestedDetailKey = requestedDetailKey,
                        onDetailKeyConsumed = onDetailKeyConsumed,
                        widgetNavRequest = widgetNavRequest,
                        onWidgetNavConsumed = onWidgetNavConsumed,
                        requestedImportUri = requestedImportUri,
                        onImportConsumed = onImportConsumed,
                        requestedInsertForm = requestedInsertForm,
                        onInsertConsumed = onInsertConsumed,
                        requestedEditKey = requestedEditKey,
                        onEditKeyConsumed = onEditKeyConsumed,
                    )
                    false -> ReminderOnboardingScreen(
                        onFinished = reminderOnboarding::finish,
                        modifier = modifier,
                    )
                    null -> {}
                }
            }
        } else {
            PermissionScreen(
                onGranted = { hasPermission = true },
                modifier = modifier,
            )
        }
    }
}
