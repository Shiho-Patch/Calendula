package de.jeanlucmakiola.calendula.ui.common

import android.icu.util.TimeZone as IcuTimeZone

/**
 * The ISO 3166 region an IANA zone belongs to ("America/New_York" -> "US"), or
 * null when ICU has none or maps it to the multi-country "001" ("Etc/UTC").
 *
 * This is the `android.icu`-backed bridge the pure-JVM
 * [de.jeanlucmakiola.calendula.domain.timeZoneOptions] takes as `regionOf`, so
 * it can ask ICU for a zone's abbreviation in that zone's own region — the only
 * region for which ICU will surface it (see `resolveAbbreviation`). Lives here,
 * not in `domain/`, precisely because it needs an Android import.
 */
fun icuTimeZoneRegion(id: String): String? =
    runCatching { IcuTimeZone.getRegion(id) }.getOrNull()?.takeIf { it.length == 2 }
