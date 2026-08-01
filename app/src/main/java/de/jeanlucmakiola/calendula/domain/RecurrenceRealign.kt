package de.jeanlucmakiola.calendula.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * [rrule] re-anchored from an occurrence on [oldStart] to one on [newStart].
 *
 * `Events.RRULE` is written verbatim while DTSTART moves, so a rule that names
 * its own day — `FREQ=WEEKLY;BYDAY=MO`, what Google and CalDAV write for nearly
 * every weekly series — would keep pointing at Monday after the anchor became a
 * Wednesday, and the series would not move at all. The day-selecting parts are
 * therefore re-derived from [newStart].
 *
 * Returns null when the rule picks days in a way one moved occurrence cannot
 * resolve (`BYDAY=MO,WE`, an ordinal `2TH`, `BYSETPOS`, …). There is no safe
 * guess there — dropping the extra days would delete occurrences — so the caller
 * refuses to move anything but that single occurrence.
 */
fun realignRecurrence(rrule: String, oldStart: LocalDate, newStart: LocalDate): String? {
    if (oldStart == newStart) return rrule
    val prefix = if (rrule.startsWith("RRULE:")) "RRULE:" else ""
    val parts = rrule.removePrefix("RRULE:").split(';').filter { it.isNotBlank() }
    if (parts.isEmpty()) return null
    val rebuilt = parts.map { part ->
        val eq = part.indexOf('=')
        if (eq <= 0) return null
        val key = part.substring(0, eq).uppercase()
        val value = part.substring(eq + 1).trim()
        when (key) {
            "BYDAY" -> {
                val old = RRULE_DAY_CODES[oldStart.dayOfWeek] ?: return null
                if (!value.equals(old, ignoreCase = true)) return null
                "BYDAY=${RRULE_DAY_CODES.getValue(newStart.dayOfWeek)}"
            }
            "BYMONTHDAY" -> {
                if (value != oldStart.day.toString()) return null
                "BYMONTHDAY=${newStart.day}"
            }
            "BYMONTH" -> {
                if (value != oldStart.month.number.toString()) return null
                "BYMONTH=${newStart.month.number}"
            }
            "FREQ", "INTERVAL", "COUNT", "UNTIL", "WKST" -> part
            else -> return null
        }
    }
    return prefix + rebuilt.joinToString(";")
}
