package de.jeanlucmakiola.calendula.ui.common

import android.icu.text.ListFormatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import de.jeanlucmakiola.calendula.R
import de.jeanlucmakiola.calendula.domain.SimpleRecurrence
import de.jeanlucmakiola.calendula.domain.occurrencesSpanYears
import de.jeanlucmakiola.calendula.domain.upcomingOccurrences
import de.jeanlucmakiola.floret.locale.localizedDateFormatter
import kotlinx.datetime.toJavaLocalDate
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * Humanise an RFC 5545 RRULE into a localized phrase, e.g.
 * "Every week on Tue and Thu until 31 Dec 2026" or "Every day, 10 times".
 * Falls back to a generic label for rules we don't render in full (ordinal
 * monthly/yearly BYDAY, etc.). Shared by the detail screen and the edit
 * form's repeat card.
 */
@Composable
fun recurrenceText(rrule: String, locale: Locale): AnnotatedString {
    val parts = rrule.removePrefix("RRULE:").split(';').mapNotNull { token ->
        val eq = token.indexOf('=')
        if (eq <= 0) null else token.substring(0, eq).uppercase() to token.substring(eq + 1)
    }.toMap()

    val freq = parts["FREQ"]?.uppercase()
    val interval = parts["INTERVAL"]?.toIntOrNull() ?: 1
    val base = when (freq) {
        "DAILY" -> if (interval == 1) stringResource(R.string.recurrence_daily)
        else stringResource(R.string.recurrence_every_n_days, interval)
        "WEEKLY" -> if (interval == 1) stringResource(R.string.recurrence_weekly)
        else stringResource(R.string.recurrence_every_n_weeks, interval)
        "MONTHLY" -> if (interval == 1) stringResource(R.string.recurrence_monthly)
        else stringResource(R.string.recurrence_every_n_months, interval)
        "YEARLY" -> if (interval == 1) stringResource(R.string.recurrence_yearly)
        else stringResource(R.string.recurrence_every_n_years, interval)
        else -> return AnnotatedString(stringResource(R.string.event_detail_recurring))
    }

    // Weekly + BYDAY → "<base> on <days>"; other BYDAY forms keep just the base.
    // The day names + their joined block are tracked so only the names (not the
    // commas/conjunction) can be italicised in the final string.
    val byDay = parts["BYDAY"]
    var dayNames: List<String>? = null
    var joinedDays: String? = null
    val main = if (freq == "WEEKLY" && !byDay.isNullOrBlank()) {
        val days = byDay.split(',').mapNotNull { rruleDayName(it.trim(), locale) }
        if (days.isNotEmpty()) {
            val joined = ListFormatter.getInstance(locale).format(days)
            dayNames = days
            joinedDays = joined
            stringResource(R.string.recurrence_on_days, base, joined)
        } else {
            base
        }
    } else {
        base
    }

    // End bound: UNTIL (a date) takes precedence over COUNT (a number of times).
    val until = parts["UNTIL"]?.let { parseUntilDate(it, locale) }
    val count = parts["COUNT"]?.toIntOrNull()
    val full = when {
        until != null -> stringResource(R.string.recurrence_with_until, main, until)
        count != null -> stringResource(R.string.recurrence_with_count, main, count)
        else -> main
    }

    return buildAnnotatedString {
        append(full)
        val names = dayNames
        val joined = joinedDays
        if (names != null && joined != null) {
            // Italicise each day name within the joined block only — leaving the
            // separators and conjunction ("und"/"and") in the regular style.
            val regionStart = full.indexOf(joined)
            if (regionStart >= 0) {
                val regionEnd = regionStart + joined.length
                var cursor = regionStart
                for (name in names) {
                    val at = full.indexOf(name, cursor)
                    if (at in regionStart until regionEnd) {
                        addStyle(SpanStyle(fontStyle = FontStyle.Italic), at, at + name.length)
                        cursor = at + name.length
                    }
                }
            }
        }
    }
}

/**
 * The rule's first few dates as one line — "Next: 30 Jul, 6 Aug, 13 Aug" — for
 * the recurrence picker.
 *
 * The phrase [recurrenceText] renders says what the rule *is*; this says what
 * it *does*, which is the part a monthly rule on the 31st or an every-other-week
 * rule with weekday picks gets wrong in people's heads. It expands the rule
 * with [upcomingOccurrences], so it inherits that function's RFC reading — and
 * its limits: only the shapes the picker itself can build.
 *
 * A rule that yields nothing at all (an end date before the start) says so
 * rather than showing an empty list, since that is a mistake worth catching
 * before saving.
 */
@Composable
fun nextOccurrencesText(
    rule: SimpleRecurrence,
    // Spelled out: in this file, the bare LocalDate is java.time's.
    start: kotlinx.datetime.LocalDate,
    locale: Locale,
): String {
    val dates = rule.upcomingOccurrences(start, limit = NEXT_OCCURRENCE_COUNT)
    if (dates.isEmpty()) return stringResource(R.string.event_edit_recurrence_next_none)
    // Years only once they carry information — a yearly rule is otherwise the
    // same date repeated, and a monthly one crossing New Year hides that it did.
    val pattern = if (occurrencesSpanYears(dates, start)) "dMMMy" else "dMMM"
    val formatter = localizedDateFormatter(locale, pattern)
    val formatted = dates.map { formatter.format(it.toJavaLocalDate()) }
    return stringResource(
        R.string.event_edit_recurrence_next,
        ListFormatter.getInstance(locale).format(formatted),
    )
}

/** Enough dates to show a rhythm (and a skipped month), few enough for one line. */
private const val NEXT_OCCURRENCE_COUNT = 3

/** Map an RRULE BYDAY token (e.g. "TU" or "2TH") to a localized short weekday name. */
private fun rruleDayName(token: String, locale: Locale): String? {
    val dow = when (token.takeLast(2).uppercase()) {
        "MO" -> DayOfWeek.MONDAY
        "TU" -> DayOfWeek.TUESDAY
        "WE" -> DayOfWeek.WEDNESDAY
        "TH" -> DayOfWeek.THURSDAY
        "FR" -> DayOfWeek.FRIDAY
        "SA" -> DayOfWeek.SATURDAY
        "SU" -> DayOfWeek.SUNDAY
        else -> return null
    }
    return dow.getDisplayName(JavaTextStyle.SHORT, locale)
}

/** Parse an RRULE UNTIL value ("20261231" or "20261231T235959Z") to a localized date. */
private fun parseUntilDate(raw: String, locale: Locale): String? {
    val date = untilLocalDate(raw) ?: return null
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
}

private val UNTIL_UTC_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)

/**
 * The calendar day an RRULE UNTIL value denotes, *in the device's zone*.
 *
 * The UTC form ("20261231T225959Z") must be converted back before its date is
 * read: `SimpleRecurrence.toRRule` writes the end of the chosen **local** day
 * expressed in UTC, so for zones behind UTC that instant already falls on the
 * following UTC date. Taking the leading digits straight off would then show
 * the day after the one the user picked. Date-only and floating forms are
 * already local and pass through unchanged.
 */
internal fun untilLocalDate(raw: String, zone: ZoneId = ZoneId.systemDefault()): LocalDate? {
    val value = raw.trim()
    return runCatching {
        LocalDateTime.parse(value, UNTIL_UTC_FORMAT)
            .atOffset(ZoneOffset.UTC)
            .atZoneSameInstant(zone)
            .toLocalDate()
    }.recoverCatching {
        val digits = value.takeWhile { it.isDigit() }
        LocalDate.of(
            digits.substring(0, 4).toInt(),
            digits.substring(4, 6).toInt(),
            digits.substring(6, 8).toInt(),
        )
    }.getOrNull()
}
