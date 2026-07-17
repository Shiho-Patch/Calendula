package de.jeanlucmakiola.calendula.domain

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * One selectable zone, resolved for display: [id] is the IANA id we store in
 * `EVENT_TIMEZONE`, [displayName] its localized name, and [offsetMinutes] its
 * offset *at a given instant* — zones shift with DST, so an offset is only
 * meaningful next to the moment it was resolved for.
 */
data class TimeZoneOption(
    val id: String,
    val displayName: String,
    val offsetMinutes: Int,
) {
    /** The trailing segment of the id ("Europe/Berlin" -> "Berlin"), underscores undone. */
    val city: String get() = id.substringAfterLast('/').replace('_', ' ')

    /** The leading segment ("Europe/Berlin" -> "Europe"); empty for bare ids like "UTC". */
    val region: String get() = id.substringBeforeLast('/', missingDelimiterValue = "")
}

/**
 * Every zone the JVM knows, localized and resolved at [at]. This is ~600
 * entries, so build it once and filter the result rather than rebuilding per
 * keystroke.
 *
 * Bare three-letter ids ("EST", "CST6CDT") and the legacy SystemV tree are
 * dropped: they're aliases the tz database keeps for compatibility, they'd
 * double up the real zones in the list, and none of them is what a user means
 * when they pick a place.
 */
fun timeZoneOptions(
    locale: Locale = Locale.getDefault(),
    at: Instant = Instant.now(),
): List<TimeZoneOption> = ZoneId.getAvailableZoneIds()
    .asSequence()
    .filter { it.contains('/') && !it.startsWith("SystemV/") }
    .map { id ->
        val zone = ZoneId.of(id)
        TimeZoneOption(
            id = id,
            displayName = zone.getDisplayName(TextStyle.FULL, locale),
            offsetMinutes = zone.rules.getOffset(at).totalSeconds / 60,
        )
    }
    .sortedWith(compareBy({ it.region }, { it.city }))
    .toList()

/**
 * Resolve a single [id] the same way [timeZoneOptions] would, or null if the tz
 * database doesn't know it — for labelling one known zone without paying to
 * build the whole catalogue.
 */
fun timeZoneOptionOf(
    id: String,
    locale: Locale = Locale.getDefault(),
    at: Instant = Instant.now(),
): TimeZoneOption? {
    val zone = runCatching { ZoneId.of(id) }.getOrNull() ?: return null
    return TimeZoneOption(
        id = id,
        displayName = zone.getDisplayName(TextStyle.FULL, locale),
        offsetMinutes = zone.rules.getOffset(at).totalSeconds / 60,
    )
}

/**
 * [options] matching [query], best matches first; a blank query returns the list
 * unchanged. Matching is accent- and case-insensitive and treats underscores as
 * spaces, so "sao paulo" finds "America/Sao_Paulo".
 *
 * Ranking puts a city that *starts with* the query above one that merely
 * contains it — typing "col" should reach Colombo before Turks_and_Caicos —
 * and the id is matched ahead of the localized name so a user who knows the
 * IANA id gets it first.
 */
fun filterTimeZones(options: List<TimeZoneOption>, query: String): List<TimeZoneOption> {
    val needle = query.normalizeForSearch()
    if (needle.isEmpty()) return options
    return options
        .mapNotNull { option ->
            val city = option.city.normalizeForSearch()
            val id = option.id.normalizeForSearch()
            val name = option.displayName.normalizeForSearch()
            val rank = when {
                city.startsWith(needle) -> 0
                name.startsWith(needle) -> 1
                city.contains(needle) -> 2
                id.contains(needle) -> 3
                name.contains(needle) -> 4
                else -> return@mapNotNull null
            }
            rank to option
        }
        .sortedWith(compareBy({ it.first }, { it.second.city }))
        .map { it.second }
}

/**
 * Lowercased, accent-stripped, underscores and slashes flattened to spaces, so
 * a query types the way a place is spoken rather than the way the tz database
 * spells it.
 */
private fun String.normalizeForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('_', ' ')
        .replace('/', ' ')
        .lowercase(Locale.ROOT)
        .trim()

/** "GMT+02:00" / "GMT-05:30" / "GMT" — the offset as shown next to a zone. */
fun formatGmtOffset(offsetMinutes: Int): String {
    if (offsetMinutes == 0) return "GMT"
    val sign = if (offsetMinutes < 0) '-' else '+'
    val abs = kotlin.math.abs(offsetMinutes)
    return "GMT%c%02d:%02d".format(sign, abs / 60, abs % 60)
}
