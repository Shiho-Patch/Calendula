package de.jeanlucmakiola.calendula.domain

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * One selectable zone, resolved for display at a given instant. [id] is the IANA
 * id we store in `EVENT_TIMEZONE` and show as the primary label (via [label]);
 * [shortName] its abbreviation ("CET", "WET", "UTC", or a "GMT+05:30" fallback);
 * [offsetMinutes] its offset. Both the abbreviation and the offset shift with
 * DST, so they're only meaningful next to the moment they were resolved for.
 *
 * [displayName] is the long localized name ("Central European Time"). It's kept
 * for search only — matching on it lets someone type "pacific" — and is *not*
 * displayed: spelled out next to the id it made the field too wide to fit.
 */
data class TimeZoneOption(
    val id: String,
    val displayName: String,
    val shortName: String,
    val offsetMinutes: Int,
) {
    /** The id as the primary label, underscores undone ("America/New York"). */
    val label: String get() = id.replace('_', ' ')

    /** The trailing segment of the id ("Europe/Berlin" -> "Berlin"), underscores undone. */
    val city: String get() = id.substringAfterLast('/').replace('_', ' ')

    /** The leading segment ("Europe/Berlin" -> "Europe"); empty for bare ids like "UTC". */
    val region: String get() = id.substringBeforeLast('/', missingDelimiterValue = "")
}

private fun optionFor(
    zone: ZoneId,
    locale: Locale,
    at: Instant,
    regionOf: (String) -> String?,
): TimeZoneOption = TimeZoneOption(
    id = zone.id,
    displayName = zone.getDisplayName(TextStyle.FULL, locale),
    shortName = resolveAbbreviation(zone.id, zone.rules.isDaylightSavings(at), locale, regionOf),
    offsetMinutes = zone.rules.getOffset(at).totalSeconds / 60,
)

/**
 * The zone abbreviation ("EDT", "CEST"), DST-correct at the resolved instant, or
 * a "GMT+05:30" form when no name exists.
 *
 * Two things make this fiddlier than it looks. First, it goes through
 * java.util.TimeZone, NOT java.time's "zzz" formatter: on Android the latter has
 * no short specific-zone names and degrades every zone to a "GMT-4" form (it
 * agrees with java.util only on desktop, which is why desktop can't catch it).
 * Second, ICU only surfaces the short name *commonly used in the display
 * locale's region* — a German-region English phone (en-DE) is shown "CEST" but
 * not "EDT", and an en-US phone the reverse. So ask in the display *language*
 * but the *zone's* region ("America/New_York" in en-US, "Europe/Berlin" in
 * en-DE) via [regionOf]. That covers almost everything; where a region still
 * yields no name (Athens in en-GR) the plain device locale sometimes does, so
 * try that next; failing both, the caller shows the offset.
 *
 * [regionOf] maps an IANA id to an ISO 3166 region and is injected because it
 * needs `android.icu`, which this pure-JVM module can't import. The default
 * (no region) leaves only the device-locale path — which is all a JVM test has
 * anyway, and enough there since desktop ICU isn't region-gated the same way.
 */
private fun resolveAbbreviation(
    id: String,
    dst: Boolean,
    locale: Locale,
    regionOf: (String) -> String?,
): String {
    fun shortIn(loc: Locale): String =
        java.util.TimeZone.getTimeZone(id).getDisplayName(dst, java.util.TimeZone.SHORT, loc)
    val regional = regionOf(id)
        ?.takeIf { it.length == 2 }
        ?.let { shortIn(Locale(locale.language, it)) }
    if (regional != null && !regional.looksLikeOffset()) return regional
    // Either no region, or the region has no name for this zone: the device
    // locale is the next-best shot, and the offset the last resort.
    return shortIn(locale)
}

/** True for the offset-style names ICU returns when a zone has no abbreviation. */
private fun String.looksLikeOffset(): Boolean = this == "UTC" || startsWith("GMT")

/**
 * Every zone the JVM knows, resolved at [at]. This is ~600 entries, so build it
 * once and filter the result rather than rebuilding per keystroke. [regionOf]
 * (see [resolveAbbreviation]) supplies the zone's region for the abbreviation.
 *
 * Bare three-letter ids ("EST", "CST6CDT") and the legacy SystemV tree are
 * dropped: they're aliases the tz database keeps for compatibility, they'd
 * double up the real zones in the list, and none of them is what a user means
 * when they pick a place.
 */
fun timeZoneOptions(
    locale: Locale = Locale.getDefault(),
    at: Instant = Instant.now(),
    regionOf: (String) -> String? = { null },
): List<TimeZoneOption> = ZoneId.getAvailableZoneIds()
    .asSequence()
    .filter { it.contains('/') && !it.startsWith("SystemV/") }
    .map { optionFor(ZoneId.of(it), locale, at, regionOf) }
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
    regionOf: (String) -> String? = { null },
): TimeZoneOption? {
    val zone = runCatching { ZoneId.of(id) }.getOrNull() ?: return null
    return optionFor(zone, locale, at, regionOf)
}

/**
 * The short descriptor shown beneath the id, e.g. "CET · GMT+01:00". "UTC" reads
 * fine alone; any other offset-shaped name is normalised to our own GMT form so
 * the offset isn't stated twice in ICU's spelling and ours.
 */
fun zoneDescriptor(option: TimeZoneOption): String {
    val abbrev = option.shortName
    return when {
        abbrev == "UTC" -> "UTC"
        abbrev.startsWith("GMT") -> formatGmtOffset(option.offsetMinutes)
        else -> "$abbrev · ${formatGmtOffset(option.offsetMinutes)}"
    }
}

/**
 * [options] matching [query], best matches first; a blank query returns the list
 * unchanged. Matching is accent- and case-insensitive and treats underscores as
 * spaces, so "sao paulo" finds "America/Sao_Paulo".
 *
 * Ranking puts a city that *starts with* the query above one that merely
 * contains it — typing "col" should reach Colombo before Turks_and_Caicos —
 * and the id is matched ahead of the localized name so a user who knows the
 * IANA id gets it first. An exact abbreviation hit ("CEST") ranks just under a
 * city prefix, so typing an abbreviation surfaces every zone that shows it
 * (all the CEST zones together); the abbreviation is the one resolved for the
 * display region, i.e. what the row actually shows.
 */
fun filterTimeZones(options: List<TimeZoneOption>, query: String): List<TimeZoneOption> {
    val needle = query.normalizeForSearch()
    if (needle.isEmpty()) return options
    return options
        .mapNotNull { option ->
            val city = option.city.normalizeForSearch()
            val id = option.id.normalizeForSearch()
            val name = option.displayName.normalizeForSearch()
            val abbrev = option.shortName.normalizeForSearch()
            val rank = when {
                city.startsWith(needle) -> 0
                abbrev == needle -> 1
                name.startsWith(needle) -> 2
                abbrev.startsWith(needle) -> 3
                city.contains(needle) -> 4
                id.contains(needle) -> 5
                name.contains(needle) -> 6
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
