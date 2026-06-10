package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
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
