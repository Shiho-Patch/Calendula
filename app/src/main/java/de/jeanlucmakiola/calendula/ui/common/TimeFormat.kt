package de.jeanlucmakiola.calendula.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The resolved clock convention for the whole UI: `true` = 24-hour ("14:00"),
 * `false` = 12-hour ("2:00 PM"). Provided once at the app root from the
 * `TimeFormatPref` preference resolved against the device's 24-hour system
 * setting, so every time label reads it without per-screen plumbing. Defaults
 * to 24-hour for previews and any composition that forgets to provide it.
 */
val LocalUse24HourFormat = staticCompositionLocalOf { true }

private const val PATTERN_24 = "HH:mm"
private const val PATTERN_12 = "h:mm a"
private const val HOUR_PATTERN_12 = "h a"
private const val HOUR_MINUTE_PATTERN_12 = "h:mm"

/** A time-of-day [DateTimeFormatter] for the resolved convention and [locale]. */
fun timeOfDayFormatter(is24Hour: Boolean, locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(if (is24Hour) PATTERN_24 else PATTERN_12, locale)

/**
 * Format a wall-clock time (hour 0..23, minute 0..59) honouring the resolved
 * [is24Hour] convention and [locale]: 24h → "14:00", 12h → "2:00 PM". Pure, so
 * it can be unit-tested and used off the main thread (widget, notifications).
 */
fun formatTimeOfDay(hour: Int, minute: Int, is24Hour: Boolean, locale: Locale): String =
    LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        .format(timeOfDayFormatter(is24Hour, locale))

/**
 * Format minutes-from-midnight (0..1440) as a time label. The end-of-day value
 * 1440 renders as "24:00" in 24h (its established reading) and as midnight
 * ("12:00 AM") in 12h, which has no 24:00 equivalent.
 */
fun formatMinuteOfDay(minutes: Int, is24Hour: Boolean, locale: Locale): String = when {
    minutes >= MINUTES_PER_DAY && is24Hour -> "24:00"
    minutes >= MINUTES_PER_DAY -> formatTimeOfDay(0, 0, is24Hour = false, locale)
    else -> formatTimeOfDay(minutes / 60, minutes % 60, is24Hour, locale)
}

/**
 * The time shown in the timeline gutter while a block is dragged: 24h →
 * "09:15", 12h → "9:15". The meridiem is dropped on purpose — the hour labels
 * around it already carry it, and the gutter is too narrow to hold it.
 */
fun formatGutterTime(minutes: Int, is24Hour: Boolean, locale: Locale): String {
    val clamped = minutes.coerceIn(0, MINUTES_PER_DAY - 1)
    val pattern = if (is24Hour) PATTERN_24 else HOUR_MINUTE_PATTERN_12
    return LocalTime.of(clamped / 60, clamped % 60)
        .format(DateTimeFormatter.ofPattern(pattern, locale))
}

/**
 * The compact hour-only label for a timeline gutter: 24h → "13" (zero-padded,
 * the prior look); 12h → "1 PM".
 */
fun formatHourLabel(hour: Int, is24Hour: Boolean, locale: Locale): String =
    if (is24Hour) {
        "%02d".format(hour)
    } else {
        LocalTime.of(hour.coerceIn(0, 23), 0)
            .format(DateTimeFormatter.ofPattern(HOUR_PATTERN_12, locale))
    }

private const val MINUTES_PER_DAY = 1_440
