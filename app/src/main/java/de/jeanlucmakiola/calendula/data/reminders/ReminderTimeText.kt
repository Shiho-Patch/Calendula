package de.jeanlucmakiola.calendula.data.reminders

import de.jeanlucmakiola.calendula.ui.common.timeOfDayFormatter
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * The one line of time context in a reminder notification. Pure so it can be
 * JVM-tested.
 *
 * Timed events that fall on a day other than [today] are prefixed with that
 * day, so a reminder fired ahead of time no longer reads as if the event were
 * today (issue #46). The prefix prefers natural language and stays short:
 *
 * - today:                "09:30 – 10:00"                 (no prefix)
 * - tomorrow / yesterday: "Tomorrow, 09:30 – 10:00"       ([tomorrowLabel] / [yesterdayLabel])
 * - elsewhere this week:  "Thu, 09:30 – 10:00"            (localized short weekday)
 * - further out:          "16 Jul, 09:30 – 10:00"         (medium date — a weekday
 *                                                          alone would be ambiguous)
 * - timed, crossing days: "11 Jun, 23:30 – 12 Jun, 00:30" (medium date + short time,
 *                                                          already unambiguous)
 * - all-day, one day:     "11 Jun 2026"
 * - all-day, multi-day:   "11 Jun 2026 – 12 Jun 2026"
 *
 * All-day instances already carry an explicit date, so they never gain a
 * relative prefix. They store UTC midnights with an exclusive end, so they are
 * read in UTC and the end day is the last *covered* day.
 */
fun reminderTimeText(
    beginMillis: Long,
    endMillis: Long,
    isAllDay: Boolean,
    zone: ZoneId,
    locale: Locale,
    is24Hour: Boolean,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    tomorrowLabel: String,
    yesterdayLabel: String,
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
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val begin = Instant.ofEpochMilli(beginMillis).atZone(zone)
    val end = Instant.ofEpochMilli(endMillis).atZone(zone)
    return if (begin.toLocalDate() == end.toLocalDate()) {
        val range = timeFormat.format(begin) + RANGE + timeFormat.format(end)
        val prefix = relativeDayPrefix(
            day = begin.toLocalDate(),
            today = today,
            firstDayOfWeek = firstDayOfWeek,
            locale = locale,
            dateFormat = dateFormat,
            tomorrowLabel = tomorrowLabel,
            yesterdayLabel = yesterdayLabel,
        )
        if (prefix == null) range else "$prefix, $range"
    } else {
        // Cross-day: medium date + the chosen short time, joined per side. Built
        // from the two formatters (not ofLocalizedDateTime) so the 12/24h choice
        // applies to the time portion too. The explicit dates already say which
        // day, so no relative prefix is layered on top.
        val dateTime = { z: java.time.ZonedDateTime -> "${dateFormat.format(z)}, ${timeFormat.format(z)}" }
        dateTime(begin) + RANGE + dateTime(end)
    }
}

/**
 * A short label for [day] relative to [today], or `null` when it *is* today (the
 * common case, which needs no prefix). Weekday names are used only within the
 * current week — a "next Wednesday" would be indistinguishable from this one, so
 * anything past this week falls back to the exact date.
 */
private fun relativeDayPrefix(
    day: LocalDate,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    locale: Locale,
    dateFormat: DateTimeFormatter,
    tomorrowLabel: String,
    yesterdayLabel: String,
): String? = when (ChronoUnit.DAYS.between(today, day)) {
    0L -> null
    1L -> tomorrowLabel
    -1L -> yesterdayLabel
    else -> if (isSameWeek(day, today, firstDayOfWeek)) {
        day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    } else {
        dateFormat.format(day)
    }
}

/**
 * True when [day] and [today] share the same week. The week boundary honours the
 * user's *week starts on* setting (already resolved to a concrete [firstDayOfWeek],
 * with [firstDayOfWeek] falling back to the locale default upstream).
 */
private fun isSameWeek(day: LocalDate, today: LocalDate, firstDayOfWeek: DayOfWeek): Boolean {
    val startOfWeek = today.previousOrSame(firstDayOfWeek)
    return !day.isBefore(startOfWeek) && day.isBefore(startOfWeek.plusWeeks(1))
}

/** The most recent [target] on or before this date (this date itself when it matches). */
private fun LocalDate.previousOrSame(target: DayOfWeek): LocalDate {
    val backtrack = (dayOfWeek.value - target.value + 7) % 7
    return minusDays(backtrack.toLong())
}

private const val RANGE = " – "
