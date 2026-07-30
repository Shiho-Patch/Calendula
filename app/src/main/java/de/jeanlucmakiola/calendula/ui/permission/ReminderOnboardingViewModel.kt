package de.jeanlucmakiola.calendula.ui.permission

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.data.reminders.ReminderScanner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Gates the one-time reminder onboarding step (v1.4) shown after the calendar
 * grant. [onboardingDone] is null until DataStore's first emission so the
 * step neither flashes for users who completed it nor gets skipped.
 */
@HiltViewModel
class ReminderOnboardingViewModel @Inject constructor(
    private val prefs: SettingsPrefs,
    private val scanner: ReminderScanner,
) : ViewModel() {

    val onboardingDone: StateFlow<Boolean?> = prefs.reminderOnboardingDone
        .map { done -> done as Boolean? }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null,
        )

    /** Close the step, recording whether reminder notifications stay on. */
    fun finish(remindersEnabled: Boolean) {
        viewModelScope.launch {
            prefs.setRemindersEnabled(remindersEnabled)
            prefs.setReminderOnboardingDone()
            // Nothing else re-arms the scan: turning reminders off cancels the
            // alarm (#75).
            scanner.scan()
        }
    }

    /**
     * Re-scan after the calendar permission is granted. The launch scan runs
     * before the grant and bails out without arming anything, so without this
     * the first alarm waits on the daily worker.
     */
    fun rearmAfterGrant() {
        scanner.scanInBackground()
    }
}
