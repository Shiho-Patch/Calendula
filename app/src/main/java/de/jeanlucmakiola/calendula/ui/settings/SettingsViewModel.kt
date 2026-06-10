package de.jeanlucmakiola.calendula.ui.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.data.prefs.ThemeMode
import de.jeanlucmakiola.calendula.data.prefs.WeekStartPref
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SettingsPrefs,
) : ViewModel() {

    private val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val state: StateFlow<SettingsUiState> =
        combine(
            prefs.themeMode,
            prefs.dynamicColor,
            prefs.weekStart,
        ) { theme, dynamic, weekStart ->
            SettingsUiState(
                themeMode = theme,
                dynamicColor = dynamic && dynamicColorAvailable,
                dynamicColorAvailable = dynamicColorAvailable,
                weekStart = weekStart,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = SettingsUiState(dynamicColorAvailable = dynamicColorAvailable),
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { prefs.setDynamicColor(enabled) }
    }

    fun setWeekStart(pref: WeekStartPref) {
        viewModelScope.launch { prefs.setWeekStart(pref) }
    }
}
