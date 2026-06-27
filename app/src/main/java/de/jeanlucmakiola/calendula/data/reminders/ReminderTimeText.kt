package de.jeanlucmakiola.calendula.data.reminders

import de.jeanlucmakiola.calendula.ui.common.timeOfDayFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The one line of time context in a reminder notification. Pure so it can be
 * JVM-tested:
 *
 * - timed, same day:      "09:30 – 10:00"
 * - timed, crossing days: "11 Jun, 23:30 – 12 Jun, 00:30" (medium date + short time)
 * - all-day, one day:     "11 Jun 2026"
 * - all-day, multi-day:   "11 Jun 2026 – 12 Jun 2026"
 *
 * All-day instances store UTC midnights with an exclusive end, so they are
 * read in UTC and the end day is the last *covered* day.
 */
fun reminderTimeText(
    beginMillis: Long,
    endMillis: Long,
    isAllDay: Boolean,
    zone: ZoneId,
    locale: Locale,
    is24Hour: Boolean,
): String {
    if (isAllDay) {
        val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        // (atZone().toLocalDate() instead of LocalDate.ofInstant — API 34+)
        val firstDay = Instant.ofEpochMilli(beginMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val lastDay = Instant.ofEpochMilli(endMillis).atZone(ZoneOffset.UTC).toLocalDate()
            .minusDays(1)
            .coerceAtLeast(firstDay)
        return if (lastDay == firstDay) {
            dateFormat.format(firstDay)
        } else {
            dateFormat.format(firstDay) + RANGE + dateFormat.format(lastDay)
        }
    }

    val timeFormat = timeOfDayFormatter(is24Hour, locale)
    val begin = Instant.ofEpochMilli(beginMillis).atZone(zone)
    val end = Instant.ofEpochMilli(endMillis).atZone(zone)
    return if (begin.toLocalDate() == end.toLocalDate()) {
        timeFormat.format(begin) + RANGE + timeFormat.format(end)
    } else {
        // Cross-day: medium date + the chosen short time, joined per side. Built
        // from the two formatters (not ofLocalizedDateTime) so the 12/24h choice
        // applies to the time portion too.
        val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        val dateTime = { z: java.time.ZonedDateTime -> "${dateFormat.format(z)}, ${timeFormat.format(z)}" }
        dateTime(begin) + RANGE + dateTime(end)
    }
}

private const val RANGE = " – "
