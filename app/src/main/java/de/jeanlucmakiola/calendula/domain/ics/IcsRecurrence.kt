package de.jeanlucmakiola.calendula.domain.ics

private val VALID_FREQ = setOf(
    "SECONDLY", "MINUTELY", "HOURLY", "DAILY", "WEEKLY", "MONTHLY", "YEARLY",
)

/** RRULE parts whose value is a comma-separated list, so empty items can be dropped. */
private val LIST_PARTS = setOf(
    "BYSECOND", "BYMINUTE", "BYHOUR", "BYDAY", "BYMONTHDAY", "BYYEARDAY",
    "BYWEEKNO", "BYMONTH", "BYSETPOS",
)

/**
 * Make a foreign RRULE safe to hand `CalendarContract`, or return null when it
 * can't be salvaged.
 *
 * The provider validates every RRULE through `EventRecurrence.parse`, which
 * **throws** on a malformed rule — and the throw surfaces from `insert`, not
 * from a later read. One bad rule in an imported file would therefore abort the
 * write it rides on. Producers do emit such rules: Fossify's exporter writes a
 * bare `;BYDAY=` whenever a weekly event carries no weekday mask
 * (`Parser.getByDay` interpolates an empty day string unconditionally), which is
 * the state every weekly event it imported from elsewhere is left in.
 *
 * Empty and malformed parts are dropped rather than failing the rule; only a
 * missing or unrecognised `FREQ` — without which there is no rule at all —
 * returns null.
 */
fun sanitizeRrule(raw: String?): String? {
    val rule = raw?.trim()?.removePrefix("RRULE:")?.trim().orEmpty()
    if (rule.isEmpty()) return null

    var freqSeen = false
    val parts = rule.split(';').mapNotNull { part ->
        val key = part.substringBefore('=', "").trim().uppercase()
        val value = if ('=' in part) part.substringAfter('=').trim() else ""
        if (key.isEmpty() || value.isEmpty()) return@mapNotNull null
        when {
            key == "FREQ" -> {
                if (value.uppercase() !in VALID_FREQ) return null
                freqSeen = true
                "FREQ=${value.uppercase()}"
            }
            // A non-positive or non-numeric INTERVAL is rejected by the provider.
            key == "INTERVAL" -> value.toIntOrNull()?.takeIf { it > 0 }?.let { "INTERVAL=$it" }
            key in LIST_PARTS -> value.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }
                ?.let { "$key=${it.joinToString(",")}" }
            else -> "$key=$value"
        }
    }
    return if (freqSeen) parts.joinToString(";") else null
}
