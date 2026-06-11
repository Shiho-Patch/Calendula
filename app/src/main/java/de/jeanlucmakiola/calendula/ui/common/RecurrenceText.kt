package de.jeanlucmakiola.calendula.ui.common

import android.icu.text.ListFormatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import de.jeanlucmakiola.calendula.R
import java.time.DayOfWeek
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
    val digits = raw.takeWhile { it.isDigit() }
    if (digits.length < 8) return null
    return try {
        val date = java.time.LocalDate.of(
            digits.substring(0, 4).toInt(),
            digits.substring(4, 6).toInt(),
            digits.substring(6, 8).toInt(),
        )
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date)
    } catch (e: Exception) {
        null
    }
}
