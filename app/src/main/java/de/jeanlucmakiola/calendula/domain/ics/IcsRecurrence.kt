package de.jeanlucmakiola.calendula.domain.ics

private val VALID_FREQ = setOf(
    "SECONDLY", "MINUTELY", "HOURLY", "DAILY", "WEEKLY", "MONTHLY", "YEARLY",
)

private val VALID_WEEKDAYS = setOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")

/**
 * What `EventRecurrence` accepts in each numeric list part: a value range, plus
 * whether `0` is legal. Its `parseNumberList` throws on a non-number, on an item
 * outside the range *and* on a zero in the by-position parts, so checking only
 * that an item is a number would still hand the provider a rule it rejects.
 *
 * `BYSETPOS` is the one part it leaves unbounded; `0` is dropped from it anyway
 * because RFC 5545 §3.3.10 gives it no meaning.
 */
private val NUMERIC_LIST_PARTS = mapOf(
    "BYSECOND" to NumericPart(0..59, zeroAllowed = true),
    "BYMINUTE" to NumericPart(0..59, zeroAllowed = true),
    "BYHOUR" to NumericPart(0..23, zeroAllowed = true),
    "BYMONTHDAY" to NumericPart(-31..31, zeroAllowed = false),
    "BYYEARDAY" to NumericPart(-366..366, zeroAllowed = false),
    "BYWEEKNO" to NumericPart(-53..53, zeroAllowed = false),
    "BYMONTH" to NumericPart(1..12, zeroAllowed = false),
    "BYSETPOS" to NumericPart(Int.MIN_VALUE..Int.MAX_VALUE, zeroAllowed = false),
)

private class NumericPart(private val range: IntRange, private val zeroAllowed: Boolean) {
    fun accepts(item: String): Boolean {
        val n = item.toIntOrNull() ?: return false
        return n in range && (zeroAllowed || n != 0)
    }
}

/** `UNTIL` in RFC 5545 basic format; anything else fails at expansion time. */
private val UNTIL_SHAPE = Regex("""\d{8}(T\d{6}Z?)?""")

/**
 * A foreign RRULE made safe to hand `CalendarContract`.
 *
 * [rule] is what survived, or null when there is nothing left to salvage.
 * [repaired] is true only when a part was actually dropped — pure normalisation
 * (case, a stray separator) doesn't count, so a conformant file is never
 * reported to the user as faulty.
 */
data class SanitizedRrule(val rule: String?, val repaired: Boolean)

/**
 * Salvage a foreign RRULE.
 *
 * The provider validates every RRULE through `EventRecurrence.parse`, which
 * **throws** on a malformed rule — and the throw surfaces from `insert`, not
 * from a later read. One bad rule in an imported file would therefore abort the
 * write it rides on. Producers do emit such rules: Fossify's exporter writes a
 * bare `;BYDAY=` whenever a weekly event carries no weekday mask
 * (`Parser.getByDay` interpolates an empty day string unconditionally), which is
 * the state every weekly event it imported from elsewhere is left in.
 *
 * That parser has a fixed table of parts and throws on any name outside it, so
 * this is a whitelist: an unrecognised part (RFC 7529 `RSCALE`, a vendor `X-`
 * extension) and an unusable value are dropped rather than passed on to fail the
 * whole event. Only a missing or unrecognised `FREQ` — without which there is no
 * rule at all — gives up entirely.
 */
fun sanitizeRrule(raw: String?): SanitizedRrule {
    val rule = raw?.trim()?.removePrefix("RRULE:")?.trim().orEmpty()
    if (rule.isEmpty()) return SanitizedRrule(rule = null, repaired = false)

    var freqSeen = false
    var repaired = false
    val parts = mutableListOf<String>()

    /** The items [valid] accepts, uppercased and comma-joined; null if none survive. */
    fun keepItems(value: String, valid: (String) -> Boolean): String? {
        val items = value.split(',').map { it.trim().uppercase() }
        val kept = items.filter(valid)
        if (kept.size != items.size) repaired = true
        return kept.takeIf { it.isNotEmpty() }?.joinToString(",")
    }

    for (part in rule.split(';')) {
        val key = part.substringBefore('=', "").trim().uppercase()
        val value = if ('=' in part) part.substringAfter('=').trim() else ""
        // A stray separator ("FREQ=DAILY;") normalises away; nothing was lost.
        if (key.isEmpty() && value.isEmpty()) continue
        val kept = when {
            key.isEmpty() || value.isEmpty() -> null
            key == "FREQ" ->
                value.uppercase().takeIf { it in VALID_FREQ }
                    ?.also { freqSeen = true }
                    ?.let { "FREQ=$it" }
            // A non-positive or non-numeric INTERVAL/COUNT is rejected by the
            // provider, and a COUNT of zero would expand to no occurrences.
            key == "INTERVAL" -> value.toIntOrNull()?.takeIf { it > 0 }?.let { "INTERVAL=$it" }
            key == "COUNT" -> value.toIntOrNull()?.takeIf { it > 0 }?.let { "COUNT=$it" }
            key == "UNTIL" ->
                value.uppercase().takeIf { UNTIL_SHAPE.matches(it) }?.let { "UNTIL=$it" }
            key == "WKST" ->
                value.uppercase().takeIf { it in VALID_WEEKDAYS }?.let { "WKST=$it" }
            key == "BYDAY" -> keepItems(value, ::isWeekdayItem)?.let { "BYDAY=$it" }
            key in NUMERIC_LIST_PARTS ->
                keepItems(value, NUMERIC_LIST_PARTS.getValue(key)::accepts)?.let { "$key=$it" }
            else -> null
        }
        if (kept == null) repaired = true else parts += kept
    }
    return if (freqSeen) {
        SanitizedRrule(rule = parts.joinToString(";"), repaired = repaired)
    } else {
        SanitizedRrule(rule = null, repaired = true)
    }
}

/** A `BYDAY` item: a two-letter weekday, optionally preceded by an ordinal. */
private fun isWeekdayItem(item: String): Boolean {
    if (item.length < 2) return false
    val ordinal = item.dropLast(2)
    return item.takeLast(2) in VALID_WEEKDAYS &&
        (ordinal.isEmpty() || ordinal.toIntOrNull() != null)
}
