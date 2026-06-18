package de.jeanlucmakiola.calendula.domain.ics

// Android's calendar provider (and Calendula's own writes) use the non-standard
// single-unit forms P<n>S / P<n>D / P<n>W — seconds without the RFC-required
// leading T. Matched first; anything else falls through to the general grammar.
private val DURATION_SINGLE_UNIT = Regex("""([+-]?)P(\d+)([WDS])""")
private val DURATION_GENERAL =
    Regex("""([+-]?)P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?""")

/**
 * Milliseconds of a DURATION (`P1D`, `P3600S`, `PT1H30M`, `-PT15M`, `P1W`, …),
 * sign-aware. Calendula writes `P<days>D` / `P<seconds>S`, but the parser also
 * accepts the general RFC 5545 grammar so backup `DURATION` rows and foreign
 * `VALARM` triggers round-trip. Unparseable input is treated as zero.
 */
fun parseRfc2445DurationMillis(duration: String?): Long {
    if (duration.isNullOrBlank()) return 0L
    val s = duration.trim()

    DURATION_SINGLE_UNIT.matchEntire(s)?.let { m ->
        val unitSeconds = when (m.groupValues[3]) {
            "W" -> 7L * 24 * 60 * 60
            "D" -> 24L * 60 * 60
            else -> 1L // S
        }
        return m.signum() * m.groupValues[2].toLong() * unitSeconds * 1_000L
    }

    val m = DURATION_GENERAL.matchEntire(s) ?: return 0L
    val weeks = m.groupValues[2].toLongOrNull() ?: 0L
    val days = m.groupValues[3].toLongOrNull() ?: 0L
    val hours = m.groupValues[4].toLongOrNull() ?: 0L
    val minutes = m.groupValues[5].toLongOrNull() ?: 0L
    val seconds = m.groupValues[6].toLongOrNull() ?: 0L
    val totalSeconds = ((((weeks * 7 + days) * 24 + hours) * 60 + minutes) * 60 + seconds)
    return m.signum() * totalSeconds * 1_000L
}

/** Sign carried by group 1 (`-` → -1, otherwise +1) of a duration match. */
private fun MatchResult.signum(): Long = if (groupValues[1] == "-") -1L else 1L
