package de.jeanlucmakiola.calendula.ui.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.jeanlucmakiola.calendula.data.backup.BackupScheduler
import de.jeanlucmakiola.calendula.data.calendar.CalendarRepository
import de.jeanlucmakiola.calendula.data.calendar.CalendarVisibilityReconciler
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
import de.jeanlucmakiola.calendula.data.prefs.CalendarPrefs
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.data.prefs.WeekStartPref
import de.jeanlucmakiola.calendula.data.reminders.ReminderScanner
import de.jeanlucmakiola.calendula.domain.CalendarSource
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.month.MonthViewStyle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the first-launch wizard (#163): decides which steps this install owes
 * (see [onboardingPlan]) and commits each answer.
 *
 * [plan] is null until the first stored emission, so the host renders nothing
 * for that frame rather than flashing a step the user has already answered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SettingsPrefs,
    private val calendarPrefs: CalendarPrefs,
    private val repository: CalendarRepository,
    private val scanner: ReminderScanner,
    private val visibilityReconciler: CalendarVisibilityReconciler,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    /** Null until the host reports it — assuming either way would flash a screen. */
    private val permissionReported = MutableStateFlow<Boolean?>(null)

    /**
     * The grant as the rest of the flow sees it: published only once the wizard
     * has been armed for it, since arming is a stored write and the frames
     * before it lands would collapse the plan to its one-step form.
     */
    private val hasPermission: StateFlow<Boolean?> = permissionReported
        .mapLatest { granted ->
            if (granted == true) armWizardIfFresh()
            granted
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    /**
     * Whether the backup step applies: something of yours is worth exporting and
     * nothing you write to is synced anywhere. Null while the calendar list
     * cannot be read: every frame before the grant, and any read that fails —
     * an empty list is also how a null cursor arrives, and guessing false there
     * would drop the step for the whole session.
     */
    private val backupApplies: Flow<Boolean?> = hasPermission.flatMapLatest { granted ->
        if (granted != true) {
            flowOf(null)
        } else {
            repository.calendars()
                .map { calendars -> if (calendars.isEmpty()) null else calendars.backupApplies() }
                .catch { emit(null) }
                .flowOn(io)
        }
    }

    /**
     * Whether the calendar step applies: the device has nothing to put events
     * in. Unlike [backupApplies] an empty list is the condition itself, not a
     * failed read — the two look identical here and both want the step, so this
     * only resolves once a non-empty list proves there is a calendar already.
     */
    private val calendarsApplies: Flow<Boolean?> = hasPermission.flatMapLatest { granted ->
        if (granted != true) {
            flowOf(null)
        } else {
            repository.calendars()
                .map { calendars -> if (calendars.isEmpty()) null else false }
                .catch { emit(null) }
                .flowOn(io)
        }
    }

    /** The calendar step's own pair: whether it applies, and whether it is answered. */
    private val calendarsFlags: Flow<CalendarsFlags> = combine(
        calendarsApplies,
        prefs.onboardingCalendarsDone,
        ::CalendarsFlags,
    )

    private val flags: Flow<OnboardingFlags> = combine(
        prefs.reminderOnboardingDone,
        prefs.onboardingWizardArmed,
        prefs.onboardingBackupDone,
        prefs.onboardingViewDone,
        prefs.onboardingMonthStyleDone,
        ::OnboardingFlags,
    )

    /** The closing steps' own flags, and whether the notice applies at all. */
    private val closingFlags: Flow<ClosingFlags> = combine(
        calendarPrefs.visibilityNoticePending.map { it == true },
        prefs.onboardingVisibilityDone,
        prefs.onboardingDoneShown,
        ::ClosingFlags,
    )

    val plan: StateFlow<OnboardingPlan?> =
        combine(
            hasPermission,
            flags,
            closingFlags,
            backupApplies,
            calendarsFlags,
        ) { granted, stored, closing, backup, calendars ->
            granted?.let {
                onboardingPlan(
                    hasPermission = it,
                    remindersDone = stored.remindersDone,
                    wizardArmed = stored.wizardArmed,
                    backupDone = stored.backupDone,
                    viewDone = stored.viewDone,
                    monthStyleDone = stored.monthStyleDone,
                    backupApplies = backup,
                    calendarsDone = calendars.done,
                    calendarsApplies = calendars.applies,
                    visibilityArmed = closing.visibilityArmed,
                    visibilityDone = closing.visibilityDone,
                    doneShown = closing.doneShown,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null,
        )

    /** The current view preferences, for the view step's initial selection. */
    val viewChoice: StateFlow<ViewChoice?> = combine(
        prefs.defaultView,
        prefs.monthViewStyle,
        prefs.weekStart,
        ::ViewChoice,
    ).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = null,
    )

    fun setHasPermission(granted: Boolean) {
        permissionReported.value = granted
    }

    /**
     * Arm the wizard for an install that has yet to answer the reminder step,
     * whatever route the grant took. Gating this on the in-app grant would
     * strand anyone who granted on Android's own settings screen and came back
     * to a restarted process, leaving them the reminder step alone.
     */
    private suspend fun armWizardIfFresh() {
        if (!prefs.reminderOnboardingDone.first()) prefs.armOnboardingWizard()
    }

    /**
     * Record the in-app grant. The launch scan ran before it and bailed out
     * without arming anything, so the first alarm would otherwise wait on the
     * daily worker.
     */
    fun onPermissionGranted() {
        scanner.scanInBackground()
    }

    /** Close the reminder step, recording whether notifications stay on. */
    fun finishReminders(remindersEnabled: Boolean) {
        viewModelScope.launch {
            prefs.setRemindersEnabled(remindersEnabled)
            prefs.setReminderOnboardingDone()
            // Nothing else re-arms the scan: turning reminders off cancels the
            // alarm (#75).
            scanner.scan()
        }
    }

    /**
     * Re-ask the step before the current one, by clearing its answer. The plan
     * derives the position from those answers, so un-setting one *is* the back
     * navigation. The calendar grant belongs to the system and cannot be
     * returned to, which [OnboardingPlan.canGoBack] already refuses.
     */
    fun goBack() {
        viewModelScope.launch {
            when (plan.value?.previous) {
                OnboardingStep.Calendars -> prefs.setOnboardingCalendarsDone(false)
                OnboardingStep.Reminders -> prefs.setReminderOnboardingDone(false)
                OnboardingStep.Backup -> prefs.setOnboardingBackupDone(false)
                OnboardingStep.View -> prefs.setOnboardingViewDone(false)
                OnboardingStep.MonthStyle -> prefs.setOnboardingMonthStyleDone(false)
                OnboardingStep.Visibility -> prefs.setOnboardingVisibilityDone(false)
                OnboardingStep.Done -> prefs.setOnboardingDoneShown(false)
                OnboardingStep.Permission, null -> Unit
            }
        }
    }

    /** Close the backup step without setting anything up. */
    fun skipBackup() {
        viewModelScope.launch { prefs.setOnboardingBackupDone() }
    }

    /**
     * Make the device-only calendar the step's editor just described, and close
     * the step.
     */
    fun createLocalCalendar(displayName: String, color: Int, description: String?) {
        viewModelScope.launch {
            runCatching {
                repository.createLocalCalendar(
                    displayName = displayName,
                    color = color,
                    description = description,
                )
            }
            // Closed either way: a provider that refused the insert will not
            // succeed on a second press, and the step is skippable regardless.
            prefs.setOnboardingCalendarsDone()
        }
    }

    /**
     * Close the calendar step without making one. An empty app is a legitimate
     * choice, and so is going off to set up an account and coming back.
     */
    fun skipCalendars() {
        viewModelScope.launch { prefs.setOnboardingCalendarsDone() }
    }

    /**
     * Turn automatic backup on, writing to the folder the user just picked
     * (taking a durable write grant so background runs can keep writing), and
     * close the step.
     */
    fun enableAutoBackup(folder: Uri) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    folder,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            prefs.setAutoBackupFolderUri(folder.toString())
            prefs.setAutoBackupEnabled(true)
            BackupScheduler.apply(
                context = context,
                enabled = true,
                intervalMinutes = prefs.autoBackupIntervalMinutes.first(),
                hasFolder = true,
            )
            // Immediate feedback: the first backup lands while onboarding runs.
            BackupScheduler.runNow(context)
            prefs.setOnboardingBackupDone()
        }
    }

    fun setDefaultView(view: CalendarView) {
        viewModelScope.launch { prefs.setDefaultView(view) }
    }

    fun setMonthViewStyle(style: MonthViewStyle) {
        viewModelScope.launch { prefs.setMonthViewStyle(style) }
    }

    /** Close a picker step — the choice itself is saved as it is made. */
    fun finishView() {
        viewModelScope.launch { prefs.setOnboardingViewDone() }
    }

    fun finishMonthStyle() {
        viewModelScope.launch { prefs.setOnboardingMonthStyleDone() }
    }

    /** Acknowledge the calendar-visibility notice (#75). */
    fun finishVisibility() {
        viewModelScope.launch {
            prefs.setOnboardingVisibilityDone()
            // Retiring the notice drops its step, so it waits until nothing
            // follows — the count must not fall on the way to a later screen.
            if (plan.value?.steps?.last() == OnboardingStep.Visibility) retireVisibilityNotice()
        }
    }

    /** Close the wizard from its last screen. */
    fun finishOnboarding() {
        viewModelScope.launch {
            prefs.finishOnboardingWizard()
            retireVisibilityNotice()
        }
    }

    /**
     * Put the one-time visibility notice away for good — nothing else clears it
     * now that the wizard owns it, and left standing it would keep its step in
     * every later plan.
     */
    private suspend fun retireVisibilityNotice() {
        if (calendarPrefs.visibilityNoticePending.first() == true) {
            calendarPrefs.setVisibilityNoticePending(false)
        }
    }

    /**
     * Fold the retired app-local calendar visibility into the device's own, and
     * arm the notice if this install has anything to announce. Runs whenever the
     * app comes up holding the permission, however it was granted — one taken on
     * Android's app-settings screen never reaches the permission step's callback.
     */
    fun reconcileCalendarVisibility() {
        viewModelScope.launch { visibilityReconciler.run() }
    }
}

/** The stored answers the plan is derived from. */
private data class OnboardingFlags(
    val remindersDone: Boolean,
    val wizardArmed: Boolean,
    val backupDone: Boolean,
    val viewDone: Boolean,
    val monthStyleDone: Boolean,
)

/** The calendar step's inputs (#287). */
private data class CalendarsFlags(val applies: Boolean?, val done: Boolean)

/** The tail of the flow: the visibility notice and the closing screen. */
private data class ClosingFlags(
    val visibilityArmed: Boolean,
    val visibilityDone: Boolean,
    val doneShown: Boolean,
)

/** What the view step starts from, and renders its previews with. */
data class ViewChoice(
    val defaultView: CalendarView,
    val monthViewStyle: MonthViewStyle,
    val weekStart: WeekStartPref,
)

/**
 * True when this device holds events that live nowhere else: at least one local
 * calendar worth exporting, and no writable synced calendar to carry them.
 * A read-only subscription is not a backup, so it does not count.
 */
private fun List<CalendarSource>.backupApplies(): Boolean {
    val exportable = any { it.isLocal && it.canModifyContents && !it.isManaged }
    val syncedTarget = any { !it.isLocal && it.canModifyContents }
    return exportable && !syncedTarget
}
