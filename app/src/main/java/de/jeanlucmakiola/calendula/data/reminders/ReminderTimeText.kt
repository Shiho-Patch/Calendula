package de.jeanlucmakiola.calendula.data.reminders

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

    val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    val begin = Instant.ofEpochMilli(beginMillis).atZone(zone)
    val end = Instant.ofEpochMilli(endMillis).atZone(zone)
    return if (begin.toLocalDate() == end.toLocalDate()) {
        timeFormat.format(begin) + RANGE + timeFormat.format(end)
    } else {
        val dateTimeFormat = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
        dateTimeFormat.format(begin) + RANGE + dateTimeFormat.format(end)
    }
}

private const val RANGE = " – "
