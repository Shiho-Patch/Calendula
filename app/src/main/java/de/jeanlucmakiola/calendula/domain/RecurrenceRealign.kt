package de.jeanlucmakiola.calendula.domain

import kotlinx.datetime.LocalDate

/**
 * [rrule] re-anchored from an occurrence on [oldStart] to one on [newStart].
 *
 * `Events.RRULE` is written verbatim while DTSTART moves, so a rule that names
 * its own day — `FREQ=WEEKLY;BYDAY=MO`, what Google and CalDAV write for nearly
 * every weekly series — would keep pointing at Monday after the anchor became a
 * Wednesday, and the series would not move at all. `BYDAY` is therefore
 * re-derived from [newStart].
 *
 * **Only weekly `BYDAY` is realigned, and only for a whole-day move.** The rule
 * has to agree with the series *anchor*, which is not the occurrence being
 * dragged: the anchor moves by the same wall-clock shift, and only weekday
 * arithmetic survives that unchanged, because it is uniform mod 7 and every
 * anchor time-of-day crosses the same number of midnights. Day-of-month does not
 * — `BYMONTHDAY=28` on a January anchor dragged from Feb 28 to Mar 1 gives an
 * anchor of Jan 29 under a `BYMONTHDAY=1` rule, which is not an instance of its
 * own rule and materialises a phantom occurrence on any client that trusts
 * DTSTART. Those rules return null instead. The caller must also refuse when the
 * shift is not a whole number of days, for the same reason (see
 * `RescheduleViewModel`).
 *
 * Null also covers rules one moved occurrence cannot resolve at all
 * (`BYDAY=MO,WE`, an ordinal `2TH`, `BYSETPOS`, …) — dropping the extra days
 * would delete occurrences. The accepted parts are deliberately a subset of what
 * [parseSimpleRecurrence] understands, so anything realignable is also a rule
 * [problems] can check the `UNTIL` of.
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
    // BYDAY is only simple on a weekly rule (matching parseSimpleRecurrence);
    // "every Monday of the month" is a shape this has not been reasoned about.
    if (!weekly && parts.any { it.substringBefore('=').trim().uppercase() == "BYDAY" }) return null
    if (parts.none { it.substringBefore('=').trim().uppercase() == "FREQ" }) return null
    return prefix + rebuilt.joinToString(";")
}
