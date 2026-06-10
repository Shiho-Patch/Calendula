package de.jeanlucmakiola.calendula.ui.settings

import de.jeanlucmakiola.calendula.data.prefs.ThemeMode
import de.jeanlucmakiola.calendula.data.prefs.WeekStartPref

/**
 * Settings screen state (M4). Persisted preferences are instant to read, so
 * there is no Loading/Failure here — only a populated Success snapshot.
 * [dynamicColorAvailable] is false below Android 12, where the toggle is shown
 * disabled.
 */
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val dynamicColorAvailable: Boolean = true,
    val weekStart: WeekStartPref = WeekStartPref.AUTO,
)
