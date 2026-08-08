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
import de.jeanlucmakiola.calendula.data.di.IoDispatcher
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
    private val repository: CalendarRepository,
    private val scanner: ReminderScanner,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    /** Null until the host reports it — assuming either way would flash a screen. */
    private val hasPermission = MutableStateFlow<Boolean?>(null)

    /**
     * Whether the backup step applies: something of yours is worth exporting and
     * nothing you write to is synced anywhere. Null while the calendar list
     * cannot be read, which is every frame before the grant.
     */
    private val backupApplies: Flow<Boolean?> = hasPermission.flatMapLatest { granted ->
        if (granted != true) {
            flowOf(null)
        } else {
            repository.calendars()
                .catch { emit(emptyList()) }
                .map { calendars -> calendars.backupApplies() }
                .flowOn(io)
        }
    }

    private val flags: Flow<OnboardingFlags> = combine(
        prefs.reminderOnboardingDone,
        prefs.onboardingWizardArmed,
        prefs.onboardingBackupDone,
        prefs.onboardingViewDone,
        prefs.onboardingMonthStyleDone,
        ::OnboardingFlags,
    )

    val plan: StateFlow<OnboardingPlan?> =
        combine(hasPermission, flags, backupApplies) { granted, stored, backup ->
            granted?.let {
                onboardingPlan(
                    hasPermission = it,
                    remindersDone = stored.remindersDone,
                    wizardArmed = stored.wizardArmed,
                    backupDone = stored.backupDone,
                    viewDone = stored.viewDone,
                    monthStyleDone = stored.monthStyleDone,
                    backupApplies = backup,
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
        hasPermission.value = granted
    }

    /**
     * Record the in-app grant. The launch scan ran before it and bailed out
     * without arming anything, so the first alarm would otherwise wait on the
     * daily worker.
     */
    fun onPermissionGranted() {
        viewModelScope.launch { prefs.armOnboardingWizard() }
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
                OnboardingStep.Reminders -> prefs.setReminderOnboardingDone(false)
                OnboardingStep.Backup -> prefs.setOnboardingBackupDone(false)
                OnboardingStep.View -> prefs.setOnboardingViewDone(false)
                OnboardingStep.MonthStyle -> prefs.setOnboardingMonthStyleDone(false)
                OnboardingStep.Permission, null -> Unit
            }
        }
    }

    /** Close the backup step without setting anything up. */
    fun skipBackup() {
        viewModelScope.launch { prefs.setOnboardingBackupDone() }
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
}

/** The stored answers the plan is derived from. */
private data class OnboardingFlags(
    val remindersDone: Boolean,
    val wizardArmed: Boolean,
    val backupDone: Boolean,
    val viewDone: Boolean,
    val monthStyleDone: Boolean,
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
