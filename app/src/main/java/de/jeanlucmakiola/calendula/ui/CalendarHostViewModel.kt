package de.jeanlucmakiola.calendula.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.ui.common.CalendarView
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
}
