package de.jeanlucmakiola.calendula.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import de.jeanlucmakiola.calendula.ui.common.IMPLEMENTED_VIEWS
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Supplies [CalendarHost] with the user's default startup view (M1). Held as a
 * nullable [StateFlow] so the host can wait for DataStore's first emission before
 * seeding its view back stack — rendering nothing for that first frame rather
 * than flashing the old hard-coded Week view and then correcting it.
 */
@HiltViewModel
class CalendarHostViewModel @Inject constructor(
    prefs: SettingsPrefs,
) : ViewModel() {

    val defaultView: StateFlow<CalendarView?> = prefs.defaultView.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = null,
    )

    /** Views the top-bar quick-switch pill cycles through, in the user's order (#24). */
    val quickSwitchViews: StateFlow<List<CalendarView>> = prefs.quickSwitchConfig
        .map { it.cycle }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = IMPLEMENTED_VIEWS,
        )

    /** Order of the views in the navigation drawer (#24); every view always shown. */
    val drawerViewOrder: StateFlow<List<CalendarView>> = prefs.drawerViewOrder
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = IMPLEMENTED_VIEWS,
        )

    /** Whether each view's jump-to-today control lives in the top bar, not the FAB (#60). */
    val todayButtonInToolbar: StateFlow<Boolean> = prefs.todayButtonInToolbar
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = false,
        )

    /**
     * Whether events may be dragged to another slot to reschedule them (#68, #173).
     * Off until the stored value arrives, even though the preference itself defaults
     * on: the wrong guess costs an enabled user a gesture for the few frames a cold
     * start takes to read DataStore, but re-arms the one thing a disabled user
     * switched off to be rid of.
     */
    val dragToReschedule: StateFlow<Boolean> = prefs.dragToReschedule
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = false,
        )
}
