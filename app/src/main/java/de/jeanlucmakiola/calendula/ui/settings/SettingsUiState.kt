package de.jeanlucmakiola.calendula.ui.settings

import de.jeanlucmakiola.calendula.data.prefs.SettingsPrefs
import de.jeanlucmakiola.calendula.data.prefs.ThemeMode
import de.jeanlucmakiola.calendula.data.prefs.WeekStartPref
import de.jeanlucmakiola.calendula.domain.EventFormField

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
    /** Optional event-form fields shown by default (rest behind "more fields"). */
    val defaultFormFields: Set<EventFormField> = SettingsPrefs.DEFAULT_FORM_FIELDS,
    /** Whether Calendula posts reminder notifications (v1.4). */
    val remindersEnabled: Boolean = true,
    /**
     * Whether the event-colour picker is offered on calendars that publish no
     * colour palette (the colour may then not survive their next sync).
     */
    val allowColorOnUnsupportedCalendars: Boolean = false,
)
