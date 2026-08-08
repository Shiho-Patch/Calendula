package de.jeanlucmakiola.calendula.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
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
import de.jeanlucmakiola.calendula.ui.common.rememberCalendarFadeSpec
import de.jeanlucmakiola.calendula.ui.onboarding.OnboardingStep
import de.jeanlucmakiola.calendula.ui.onboarding.OnboardingSteps
import de.jeanlucmakiola.calendula.ui.onboarding.OnboardingViewModel
import de.jeanlucmakiola.calendula.ui.onboarding.onboardingSlideTransition
import de.jeanlucmakiola.calendula.ui.onboarding.rememberOnboardingSlideSpec
import de.jeanlucmakiola.floret.identity.rememberReduceMotion

/** What the root is showing: a wizard step, the app, or neither yet. */
private sealed interface RootTarget {
    /** DataStore has not emitted yet — render nothing rather than the wrong screen. */
    data object Loading : RootTarget
    data object App : RootTarget
    data class Step(val step: OnboardingStep) : RootTarget
}

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
    requestedInsertSource: de.jeanlucmakiola.calendula.ui.edit.ImportSource =
        de.jeanlucmakiola.calendula.ui.edit.ImportSource.Insert,
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

    val onboarding: OnboardingViewModel = hiltViewModel()
    val plan by onboarding.plan.collectAsStateWithLifecycle()
    // Runs however the permission was granted, including via Android's
    // app-settings screen (caught by the ON_RESUME above).
    LaunchedEffect(hasPermission) {
        onboarding.setHasPermission(hasPermission)
        if (hasPermission && !grantedAtLaunch) onboarding.onPermissionGranted()
    }

    // One-time explainer for the switch to the device's own calendar visibility
    // (#75), armed by the reconciler.
    val visibilityNotice: CalendarVisibilityNoticeViewModel = hiltViewModel()
    val noticePending by visibilityNotice.pending.collectAsStateWithLifecycle()
    LaunchedEffect(hasPermission) {
        if (hasPermission) visibilityNotice.reconcile()
    }

    val current = plan
    val target: RootTarget = when {
        current == null -> RootTarget.Loading
        current.current == null -> RootTarget.App
        else -> RootTarget.Step(current.current)
    }

    if (target is RootTarget.App && noticePending) {
        CalendarVisibilityNoticeDialog(onDismiss = visibilityNotice::dismiss)
    }

    // Which way the flow is moving, so a step-back slides back. The app sits
    // after every step and the blank first frame before them, so finishing the
    // wizard reads as one more move forward.
    val ordinal = when (target) {
        RootTarget.Loading -> Int.MIN_VALUE
        RootTarget.App -> Int.MAX_VALUE
        is RootTarget.Step -> current?.steps?.indexOf(target.step) ?: 0
    }
    var lastOrdinal by remember { mutableIntStateOf(ordinal) }
    val slideDir = if (ordinal < lastOrdinal) -1 else 1
    SideEffect { lastOrdinal = ordinal }

    // The wizard swipes: the steps sit side by side and the whole width travels,
    // so the flow reads as moving through a stack of screens rather than as one
    // screen redrawing itself.
    val slideSpec = rememberOnboardingSlideSpec()
    val fadeSpec = rememberCalendarFadeSpec()
    val reduceMotion = rememberReduceMotion()
    AnimatedContent(
        targetState = target,
        transitionSpec = {
            // Coming off the blank first frame is not a step change — the app
            // has only just opened, so the first screen simply appears.
            if (initialState == RootTarget.Loading) {
                fadeIn(fadeSpec).togetherWith(fadeOut(fadeSpec))
            } else {
                onboardingSlideTransition(
                    slideDir = slideDir,
                    spec = slideSpec,
                    fadeSpec = fadeSpec,
                    reduceMotion = reduceMotion,
                )
            }
        },
        label = "onboardingGate",
    ) { shown ->
        when (shown) {
            RootTarget.Loading -> Unit
            RootTarget.App -> CalendarHost(
                modifier = modifier,
                requestedDetailKey = requestedDetailKey,
                onDetailKeyConsumed = onDetailKeyConsumed,
                widgetNavRequest = widgetNavRequest,
                onWidgetNavConsumed = onWidgetNavConsumed,
                requestedImportUri = requestedImportUri,
                onImportConsumed = onImportConsumed,
                requestedInsertForm = requestedInsertForm,
                requestedInsertSource = requestedInsertSource,
                onInsertConsumed = onInsertConsumed,
                requestedEditKey = requestedEditKey,
                onEditKeyConsumed = onEditKeyConsumed,
            )
            // The plan the outgoing step was drawn with is gone by the time the
            // transition ends, so the live one carries both halves; each reads
            // its own position out of it.
            is RootTarget.Step -> current?.let { plan ->
                OnboardingSteps(
                    step = shown.step,
                    plan = plan,
                    viewModel = onboarding,
                    onPermissionGranted = { hasPermission = true },
                    modifier = modifier,
                )
            }
        }
    }
}
