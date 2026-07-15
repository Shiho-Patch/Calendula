package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Current display [Locale], read observably from [LocalConfiguration] so the UI
 * recomposes after a locale change (lint: NonObservableLocale). Used for
 * weekday/month name formatting.
 */
@Composable
fun currentLocale(): Locale {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        ConfigurationCompat.getLocales(configuration).get(0) ?: Locale.getDefault()
    }
}

/**
 * A [DateTimeFormatter] for [skeleton]'s fields laid out in [locale]'s own order
 * (via Android's best-pattern matching), so dates read naturally per locale
 * instead of a hardcoded day-month-year order. [skeleton] lists the wanted
 * fields, e.g. "dMMMy" (day, abbreviated month, year) or "EEEdMMM" (weekday too).
 */
fun localizedDateFormatter(locale: Locale, skeleton: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(
        android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton),
        locale,
    )
