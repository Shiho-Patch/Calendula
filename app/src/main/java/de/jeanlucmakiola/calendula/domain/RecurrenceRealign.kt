package de.jeanlucmakiola.calendula.domain

import kotlinx.datetime.LocalDate

/**
 * [rrule] re-anchored from an occurrence on [oldStart] to one on [newStart].
 *
 * `Events.RRULE` is written verbatim while DTSTART moves, so `FREQ=WEEKLY;BYDAY=MO`
 * would keep naming Monday after the anchor became a Wednesday and the series
 * would not move at all — `BYDAY` is re-derived from [newStart].
 *
 * Only weekly `BYDAY` is realigned, and only for a whole-day move: the rule has
 * to agree with the series *anchor*, and weekday arithmetic is the only kind
 * that survives the same wall-clock shift unchanged. Everything else returns
 * null, as do rules one moved occurrence can't resolve (`BYDAY=MO,WE`, `2TH`,
 * `BYSETPOS`). The accepted parts are a subset of [parseSimpleRecurrence], so
 * anything realignable is also a rule [problems] can check the `UNTIL` of.
 */
fun realignRecurrence(rrule: String, oldStart: LocalDate, newStart: LocalDate): String? {
    if (oldStart == newStart) return rrule
    val prefix = if (rrule.startsWith("RRULE:")) "RRULE:" else ""
    val parts = rrule.removePrefix("RRULE:").split(';').filter { it.isNotBlank() }
    if (parts.isEmpty()) return null
    var weekly = false
    val rebuilt = parts.map { part ->
        val eq = part.indexOf('=')
        if (eq <= 0) return null
        val key = part.substring(0, eq).uppercase()
        val value = part.substring(eq + 1).trim()
        when (key) {
            "FREQ" -> {
                weekly = value.equals("WEEKLY", ignoreCase = true)
                part
            }
            "BYDAY" -> {
                val old = RRULE_DAY_CODES[oldStart.dayOfWeek] ?: return null
                if (!value.equals(old, ignoreCase = true)) return null
                "BYDAY=${RRULE_DAY_CODES.getValue(newStart.dayOfWeek)}"
            }
            "INTERVAL", "COUNT", "UNTIL", "WKST" -> part
            else -> return null
        }
    }
    // BYDAY is only simple on a weekly rule, matching parseSimpleRecurrence.
    if (!weekly && parts.any { it.substringBefore('=').trim().uppercase() == "BYDAY" }) return null
    if (parts.none { it.substringBefore('=').trim().uppercase() == "FREQ" }) return null
    return prefix + rebuilt.joinToString(";")
}
