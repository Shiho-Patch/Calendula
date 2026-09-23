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
import de.jeanlucmakiola.calendula.domain.hasVisibilitySwitch
import de.jeanlucmakiola.calendula.domain.orderedForManager
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.month.MonthViewStyle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** How the step's own create attempt ended, while it is still saying so (#287). */
    private val _creation = MutableStateFlow<CalendarCreation?>(null)
    val creation: StateFlow<CalendarCreation?> = _creation.asStateFlow()

    /**
     * Whether the step made a calendar here. Latched for the session and never
     * cleared: the new calendar answers [calendarsApplies] with false, which
     * would drop the step out of the plan the moment it is used — first from
     * under the message that it worked, and then from the step count, which
     * would fall by one just as the user pressed Continue.
     */
    private val calendarCreatedHere = MutableStateFlow(false)

    /**
     * Whether the calendar step applies: the device has nothing to show and so
     * nothing to put events in. Unlike [backupApplies] an empty list is the
     * condition itself, not a failed read — the two look identical here and both
     * want the step, so this only resolves once a calendar the app can actually
     * show has arrived. Calendars that exist but are all switched off leave it
     * unresolved too: the step in front (#287) offers to switch them on, and
     * declining that lands here, on the same offer a device with none gets.
     */
    private val calendarsApplies: Flow<Boolean?> = combine(
        hasPermission.flatMapLatest { granted ->
            if (granted != true) {
                flowOf(null)
            } else {
                val applies: Flow<Boolean?> = repository.calendars()
                    .map { calendars -> if (calendars.any { it.isVisibleInSystem }) false else null }
                applies.catch { emit(null) }.flowOn(io)
            }
        },
        calendarCreatedHere,
    ) { applies, latched -> if (latched) null else applies }

    /** Set once the step has switched something, so its own writes keep it in the plan. */
    private val hiddenTouched = MutableStateFlow(false)

    /** A visibility write the provider did not take, while the step says so (#287). */
    private val _hiddenWriteFailed = MutableStateFlow(false)
    val hiddenWriteFailed: StateFlow<Boolean> = _hiddenWriteFailed.asStateFlow()

    /**
     * Whether the device is showing nothing at all right now: calendars exist,
     * none is visible, and at least one of them has a switch the step can flip
     * (#287). Null only while there is no list to judge — before the grant, and
     * on a read that failed — which keeps the step in the plan until the answer
     * arrives, the same shrink-only contract [backupApplies] keeps.
     *
     * An empty list answers false rather than null, unlike [calendarsApplies]:
     * a phone with no calendars at all is the next step's business, and this one
     * would be listing calendars it cannot see. Latched to null once the step
     * has switched something on, which would otherwise answer false and drop the
     * step out from under the list the user is still reading.
     */
    private val hiddenApplies: Flow<Boolean?> = combine(
        hasPermission.flatMapLatest { granted ->
            if (granted != true) {
                flowOf(null)
            } else {
                val hidden: Flow<Boolean?> = repository.calendars().map { it.allHidden() }
                hidden.catch { emit(null) }.flowOn(io)
            }
        },
        hiddenTouched,
    ) { hidden, touched -> if (touched) null else hidden }

    /** Both calendar steps' inputs: what each applies to, and whether it is answered. */
    private val calendarsFlags: Flow<CalendarsFlags> = combine(
        calendarsApplies,
        prefs.onboardingCalendarsDone,
        hiddenApplies,
        prefs.onboardingHiddenCalendarsDone,
        ::CalendarsFlags,
    )

    /**
     * The calendars the hidden-calendars step lists, so the user can judge which
     * to switch on rather than take the app's word that there are some. Live: a
     * row follows its own switch, and the ones without one stay listed anyway —
     * being told a calendar exists is half the point.
     */
    val hiddenCalendars: StateFlow<List<CalendarSource>> = hasPermission
        .flatMapLatest { granted ->
            if (granted != true) {
                flowOf(emptyList())
            } else {
                repository.calendars().catch { emit(emptyList()) }.flowOn(io)
            }
        }
        .map { it.orderedForManager() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList(),
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
                    hiddenDone = calendars.hiddenDone,
                    hiddenApplies = calendars.hiddenApplies,
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
                OnboardingStep.HiddenCalendars -> prefs.setOnboardingHiddenCalendarsDone(false)
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
     * Make the device-only calendar the step's editor just described. The step
     * stays up on [creation] either way — to confirm the calendar and say where
     * to make more, or to say the provider refused it. Answering the step for
     * them would leave them where the step exists to stop them being: past it,
     * with nowhere to put an event.
     */
    fun createLocalCalendar(displayName: String, color: Int, description: String?) {
        viewModelScope.launch {
            val name = displayName.trim()
            _creation.value = runCatching {
                repository.createLocalCalendar(
                    displayName = name,
                    color = color,
                    description = description,
                )
            }.fold(
                onSuccess = {
                    calendarCreatedHere.value = true
                    CalendarCreation.Created(name)
                },
                onFailure = { CalendarCreation.Failed },
            )
        }
    }

    /** Close the calendar step once its confirmation has been read. */
    fun finishCalendars() {
        viewModelScope.launch {
            prefs.setOnboardingCalendarsDone()
            _creation.value = null
        }
    }

    /** Drop the failed attempt so the step offers itself again. */
    fun clearCalendarCreation() {
        _creation.value = null
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

    /** Switch one listed calendar on or off, from the hidden-calendars step (#287). */
    fun setHiddenCalendarVisible(id: Long, visible: Boolean) = visibilityWrite {
        repository.setCalendarsVisible(listOf(id), visible)
        calendarList().any { it.id == id && it.isVisibleInSystem == visible }
    }

    /** Switch every hidden calendar back on — the step's one-tap way out (#287). */
    fun switchAllCalendarsOn() = visibilityWrite {
        val hidden = calendarList().filter { it.hasVisibilitySwitch && !it.isVisibleInSystem }
        repository.setCalendarsVisible(hidden.map { it.id }, true)
        !calendarList().allHidden()
    }

    /**
     * Run one of the step's visibility writes, whose lambda answers whether it
     * took. Checked rather than assumed: with read-only calendar access the
     * choice is kept app-side (#75) and nothing the grid shows would change.
     */
    private fun visibilityWrite(landed: suspend () -> Boolean) {
        viewModelScope.launch {
            hiddenTouched.value = true
            _hiddenWriteFailed.value = !runCatching { landed() }.getOrDefault(false)
        }
    }

    private suspend fun calendarList(): List<CalendarSource> = repository.calendars().first()

    /** Close the hidden-calendars step, switched on or left as they were (#287). */
    fun finishHiddenCalendars() {
        viewModelScope.launch { prefs.setOnboardingHiddenCalendarsDone() }
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

/** How the calendar step's create attempt ended (#287). */
sealed interface CalendarCreation {
    data class Created(val name: String) : CalendarCreation
    data object Failed : CalendarCreation
}

/** Both calendar steps' inputs (#287). */
private data class CalendarsFlags(
    val applies: Boolean?,
    val done: Boolean,
    val hiddenApplies: Boolean?,
    val hiddenDone: Boolean,
)

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
 * True when this device would show nothing and the step could change that: no
 * calendar is visible, and at least one of them has a switch worth flipping —
 * a non-syncing calendar holds no events on the device, so switching it on
 * leaves the grid exactly as empty (#287).
 */
private fun List<CalendarSource>.allHidden(): Boolean =
    any { it.hasVisibilitySwitch } && none { it.isVisibleInSystem }

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
