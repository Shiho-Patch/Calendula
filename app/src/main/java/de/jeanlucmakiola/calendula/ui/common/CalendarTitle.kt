package de.jeanlucmakiola.calendula.ui.common

import de.jeanlucmakiola.floret.locale.localizedDateFormatter
import java.util.Locale

/**
 * Formats a calendar title (top bar or widget header): [skeleton]'s fields laid
 * out in [locale]'s own order, with the year shown only when [date] falls outside
 * [currentYear].
 *
 * Pass [skeleton] *without* a year field — "LLLL" for a month, "EEEdMMM" for a
 * day. A skeleton is a field list, so wanting the year is just asking for one
 * more field; the locale still decides where it lands ("July 2026" vs "2026年7月").
 *
 * Dropping the year in the current year is the one bit of policy here: the title
 * sits directly above a grid that already says which year it is, and the year's
 * *absence* is itself the signal that you're in the current one — it appears the
 * moment you page out of it, which is when it starts carrying information.
 */
fun formatCalendarTitle(
    date: java.time.LocalDate,
    locale: Locale,
    currentYear: Int,
    skeleton: String,
): String {
    val fields = if (date.year == currentYear) skeleton else skeleton + "y"
    return localizedDateFormatter(locale, fields).format(date)
}
