package de.jeanlucmakiola.calendula.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** UI-facing language choice. AUTO follows the system languages. */
enum class LanguagePref { AUTO, GERMAN, ENGLISH }

/**
 * Per-app language via AppCompatDelegate. On API 33+ this delegates to the
 * platform per-app-languages API; below that the appcompat backport persists
 * the choice itself (manifest `autoStoreLocales` service), so we don't mirror
 * it in DataStore. Setting a locale recreates the activity, which re-reads the
 * current value for the dropdown.
 */
object AppLanguage {

    fun current(): LanguagePref {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return LanguagePref.AUTO
        return when (locales[0]?.language) {
            "de" -> LanguagePref.GERMAN
            "en" -> LanguagePref.ENGLISH
            else -> LanguagePref.AUTO
        }
    }

    fun apply(pref: LanguagePref) {
        val locales = when (pref) {
            LanguagePref.AUTO -> LocaleListCompat.getEmptyLocaleList()
            LanguagePref.GERMAN -> LocaleListCompat.forLanguageTags("de")
            LanguagePref.ENGLISH -> LocaleListCompat.forLanguageTags("en")
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
